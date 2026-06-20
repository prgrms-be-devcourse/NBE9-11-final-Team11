package com.fxflow.domain.remittancetransaction.service;

import com.fxflow.domain.companypool.service.CompanyPoolService;
import com.fxflow.domain.mockbankaccount.service.MockBankAccountService;
import com.fxflow.domain.remittancetransaction.entity.RemittanceTransaction;
import com.fxflow.domain.remittancetransaction.enums.TransferStatus;
import com.fxflow.domain.remittancetransaction.errorcode.RemittanceTransactionErrorCode;
import com.fxflow.domain.remittancetransaction.repository.RemittanceTransactionRepository;
import com.fxflow.domain.userlimitusage.entity.UserAnnualUsage;
import com.fxflow.domain.userlimitusage.repository.UserAnnualUsageRepository;
import com.fxflow.global.exception.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RemittanceRefundService {

    private static final String KRW = "KRW";

    private final RemittanceTransactionRepository remittanceTransactionRepository;
    private final CompanyPoolService companyPoolService;
    private final MockBankAccountService mockBankAccountService;
    private final UserAnnualUsageRepository userAnnualUsageRepository;

    /**
     * TRF-08 지급 실패 후 환불을 새 트랜잭션에서 처리한다.
     * 지급 트랜잭션이 rollback-only가 되어도 환불과 FAILED 상태 기록은 독립적으로 커밋될 수 있다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void refundAfterPayoutFailure(Long transferId, RuntimeException cause) {
        RemittanceTransaction remittanceTransaction = remittanceTransactionRepository.findById(transferId)
                .orElseThrow(() -> new BusinessException(
                        RemittanceTransactionErrorCode.REMITTANCE_TRANSACTION_NOT_FOUND
                ));

        String failureReason = cause.getMessage();
        String refundJournalId = createRefundJournalId(transferId);
        BigDecimal refundAmount = remittanceTransaction.getAmountKrw().add(remittanceTransaction.getFeeAmount());
        String refId = String.valueOf(transferId);

        // 환불은 TRF-07에서 증가했던 회사 KRW 풀을 차감하고 송금자 모의계좌로 되돌리는 흐름이다.
        companyPoolService.withdrawForRemittance(
                refundJournalId,
                KRW,
                refundAmount,
                transferId
        );
        mockBankAccountService.refundForRemittance(
                refundJournalId,
                remittanceTransaction.getSourceMockAccountId(),
                refundAmount,
                KRW,
                refId
        );
        releaseReservedAnnualLimit(remittanceTransaction);
        remittanceTransaction.fail(failureReason);

        log.warn(
                "해외송금 지급 실패 후 환불 처리 완료. transferId={}, reason={}",
                transferId,
                failureReason
        );
    }

    /**
     * 환불 처리 자체가 실패한 경우 별도 트랜잭션에서 운영자 확인 필요 상태로 기록한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRefundFailed(Long transferId, RuntimeException cause) {
        RemittanceTransaction remittanceTransaction = remittanceTransactionRepository.findById(transferId)
                .orElseThrow(() -> new BusinessException(
                        RemittanceTransactionErrorCode.REMITTANCE_TRANSACTION_NOT_FOUND
                ));

        releaseReservedAnnualLimit(remittanceTransaction);
        remittanceTransaction.refundFailed(cause.getMessage());

        log.error(
                "[CRITICAL-FINANCIAL-ERROR] 해외송금 환불 실패 상태 기록 완료. transferId={}, reason={}",
                transferId,
                cause.getMessage()
        );
    }

    private String createRefundJournalId(Long transferId) {
        return "JRN-TRF-" + transferId + "-REFUND";
    }

    /**
     * 주문 생성 시 선점한 연간 송금 한도를 송금 실패 시 복구한다.
     * 이미 실패 상태로 기록된 거래는 중복 차감을 막기 위해 건너뛴다.
     */
    private void releaseReservedAnnualLimit(RemittanceTransaction remittanceTransaction) {
        if (remittanceTransaction.getStatus() == TransferStatus.FAILED
                || remittanceTransaction.getStatus() == TransferStatus.REFUND_FAILED) {
            return;
        }

        int currentYear = LocalDate.now(ZoneId.of("Asia/Seoul")).getYear();

        userAnnualUsageRepository
                .findByUserIdAndYearForUpdate(remittanceTransaction.getUserId(), currentYear)
                .ifPresentOrElse(
                        usage -> subtractAnnualUsage(usage, remittanceTransaction.getAmountUsd()),
                        () -> log.warn(
                                "해외송금 실패 한도 복구 대상 사용량을 찾을 수 없습니다. transferId={}, userId={}, year={}",
                                remittanceTransaction.getId(),
                                remittanceTransaction.getUserId(),
                                currentYear
                        )
                );
    }

    private void subtractAnnualUsage(UserAnnualUsage usage, BigDecimal amountUsd) {
        usage.subtractUsage(amountUsd);
    }
}
