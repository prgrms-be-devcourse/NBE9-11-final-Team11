package com.fxflow.domain.remittancetransaction.service;

import com.fxflow.domain.companypool.service.CompanyPoolService;
import com.fxflow.domain.mockbankaccount.service.MockBankAccountService;
import com.fxflow.domain.remittancetransaction.entity.Recipient;
import com.fxflow.domain.remittancetransaction.entity.RemittanceTransaction;
import com.fxflow.domain.remittancetransaction.enums.TransferStatus;
import com.fxflow.domain.remittancetransaction.errorcode.RecipientErrorCode;
import com.fxflow.domain.remittancetransaction.errorcode.RemittanceTransactionErrorCode;
import com.fxflow.domain.remittancetransaction.repository.RecipientRepository;
import com.fxflow.domain.remittancetransaction.repository.RemittanceTransactionRepository;
import com.fxflow.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class RemittancePayoutService {

    private static final String KRW = "KRW";

    private final RemittanceTransactionRepository remittanceTransactionRepository;
    private final RecipientRepository recipientRepository;
    private final CompanyPoolService companyPoolService;
    private final MockBankAccountService mockBankAccountService;

    /**
     * 입금 완료(FUNDED)된 송금 건에 대해 외화풀 차감 및 수취인 모의계좌 입금을 처리한다.
     * 외화 지급 실패 시 이미 입금된 원화 금액은 송금자 모의계좌로 환불한다.
     */
    @Transactional
    public void processPayout(Long transferId) {
        RemittanceTransaction remittanceTransaction = getTransfer(transferId);

        // 입금 완료 이벤트가 중복 발행되더라도 이미 후속 상태로 넘어간 거래는 다시 지급하지 않는다.
        if (remittanceTransaction.getStatus() != TransferStatus.FUNDED) {
            log.info(
                    "해외송금 지급 처리 스킵. transferId={}, status={}",
                    transferId,
                    remittanceTransaction.getStatus()
            );
            return;
        }

        remittanceTransaction.startProcessing();

        try {
            Recipient recipient = getRecipient(remittanceTransaction.getRecipientId());
            String payoutJournalId = createPayoutJournalId(transferId);
            String refId = String.valueOf(transferId);

            // TRF-08: 회사 외화풀에서 지급 외화를 차감하고, 송금자가 입력한 수취인 계좌번호로 입금한다.
            companyPoolService.withdrawForRemittance(
                    payoutJournalId,
                    remittanceTransaction.getReceiveCurrency(),
                    remittanceTransaction.getReceiveAmount(),
                    transferId
            );
            Long targetMockAccountId = mockBankAccountService.depositForRemittance(
                    payoutJournalId,
                    recipient.getAccountNumber(),
                    remittanceTransaction.getReceiveAmount(),
                    remittanceTransaction.getReceiveCurrency(),
                    refId
            );

            remittanceTransaction.complete(targetMockAccountId);
        } catch (RuntimeException e) {
            // 외화 지급 실패 시 원화 입금 사실은 보존하고, 입금액+수수료를 송금자에게 환불한다.
            refundAfterPayoutFailure(remittanceTransaction, e);
        }
    }

    private RemittanceTransaction getTransfer(Long transferId) {
        return remittanceTransactionRepository.findById(transferId)
                .orElseThrow(() -> new BusinessException(
                        RemittanceTransactionErrorCode.REMITTANCE_TRANSACTION_NOT_FOUND
                ));
    }

    private Recipient getRecipient(Long recipientId) {
        return recipientRepository.findById(recipientId)
                .orElseThrow(() -> new BusinessException(RecipientErrorCode.RECIPIENT_NOT_FOUND));
    }

    private void refundAfterPayoutFailure(RemittanceTransaction remittanceTransaction, RuntimeException cause) {
        String failureReason = cause.getMessage();
        String refundJournalId = createRefundJournalId(remittanceTransaction.getId());
        BigDecimal refundAmount = remittanceTransaction.getAmountKrw().add(remittanceTransaction.getFeeAmount());
        String refId = String.valueOf(remittanceTransaction.getId());

        // 환불은 TRF-07에서 증가했던 회사 KRW 풀을 차감하고 송금자 모의계좌로 되돌리는 흐름이다.
        companyPoolService.withdrawForRemittance(
                refundJournalId,
                KRW,
                refundAmount,
                remittanceTransaction.getId()
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
                remittanceTransaction.getId(),
                failureReason
        );
    }

    private String createPayoutJournalId(Long transferId) {
        return "JRN-TRF-" + transferId + "-PAYOUT";
    }

    private String createRefundJournalId(Long transferId) {
        return "JRN-TRF-" + transferId + "-REFUND";
    }
}