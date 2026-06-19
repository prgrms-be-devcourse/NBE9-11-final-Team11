package com.fxflow.domain.remittancetransaction.service;

import com.fxflow.domain.companypool.service.CompanyPoolService;
import com.fxflow.domain.mockbankaccount.service.MockBankAccountService;
import com.fxflow.domain.remittancetransaction.entity.RemittanceTransaction;
import com.fxflow.domain.remittancetransaction.errorcode.RemittanceTransactionErrorCode;
import com.fxflow.domain.remittancetransaction.repository.RemittanceTransactionRepository;
import com.fxflow.global.exception.BusinessException;
import java.math.BigDecimal;
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

        remittanceTransaction.refundFailed(cause.getMessage());

        log.error(
                "해외송금 환불 실패 상태 기록 완료. transferId={}, reason={}",
                transferId,
                cause.getMessage()
        );
    }

    private String createRefundJournalId(Long transferId) {
        return "JRN-TRF-" + transferId + "-REFUND";
    }
}
