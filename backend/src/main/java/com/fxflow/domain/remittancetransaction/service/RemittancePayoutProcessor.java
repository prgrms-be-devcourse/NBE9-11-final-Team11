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

@Slf4j
@Service
@RequiredArgsConstructor
public class RemittancePayoutProcessor {

    private final RemittanceTransactionRepository remittanceTransactionRepository;
    private final RecipientRepository recipientRepository;
    private final CompanyPoolService companyPoolService;
    private final MockBankAccountService mockBankAccountService;

    /**
     * TRF-08 정상 지급을 하나의 트랜잭션으로 처리한다.
     * 중간 단계에서 실패하면 이 트랜잭션만 롤백되고, 환불은 별도 트랜잭션에서 수행된다.
     */
    @Transactional
    public void process(Long transferId) {
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

        Recipient recipient = getRecipient(remittanceTransaction.getRecipientId());
        String payoutJournalId = remittanceTransaction.getJournalId();
        String refId = remittanceTransaction.getJournalId();

        // TRF-08: 회사 외화풀에서 지급 외화를 차감하고, 송금자가 입력한 수취인 계좌번호로 입금한다.
        companyPoolService.withdrawForRemittance(
                payoutJournalId,
                remittanceTransaction.getReceiveCurrency(),
                remittanceTransaction.getReceiveAmount(),
                refId
        );
        Long targetMockAccountId = mockBankAccountService.depositForRemittance(
                payoutJournalId,
                recipient.getAccountNumber(),
                remittanceTransaction.getReceiveAmount(),
                remittanceTransaction.getReceiveCurrency(),
                refId
        );

        remittanceTransaction.complete(targetMockAccountId);
    }

    private RemittanceTransaction getTransfer(Long transferId) {
        return remittanceTransactionRepository.findByIdForUpdate(transferId)
                .orElseThrow(() -> new BusinessException(
                        RemittanceTransactionErrorCode.REMITTANCE_TRANSACTION_NOT_FOUND
                ));
    }

    private Recipient getRecipient(Long recipientId) {
        return recipientRepository.findById(recipientId)
                .orElseThrow(() -> new BusinessException(RecipientErrorCode.RECIPIENT_NOT_FOUND));
    }

}
