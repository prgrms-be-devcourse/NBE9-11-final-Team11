package com.fxflow.domain.remittancetransaction.dto.response;

import com.fxflow.domain.remittancetransaction.entity.RemittanceTransaction;
import com.fxflow.domain.remittancetransaction.entity.Recipient;
import com.fxflow.domain.remittancetransaction.enums.TransferStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RemittanceTransactionDetailResponse(
        Long transferId,
        Long recipientId,
        String recipientName,
        String recipientCountryCode,
        String recipientCurrencyCode,
        String recipientBankName,
        String recipientAccountNumber,
        String method,
        Long sourceMockAccountId,
        Long targetMockAccountId,
        String sendCurrency,
        BigDecimal sendAmount,
        String receiveCurrency,
        BigDecimal receiveAmount,
        BigDecimal appliedRate,
        BigDecimal feeAmount,
        BigDecimal amountKrw,
        BigDecimal amountUsd,
        String reason,
        String reasonDetail,
        TransferStatus status,
        String failureReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    /**
     * 송금 상세 조회 응답을 생성한다.
     * 수취인 정보는 recipientId로 연결된 수취인 주소록에서 조회한다.
     */
    public static RemittanceTransactionDetailResponse from(
            RemittanceTransaction remittanceTransaction,
            Recipient recipient
    ) {
        return new RemittanceTransactionDetailResponse(
                remittanceTransaction.getId(),
                remittanceTransaction.getRecipientId(),
                recipient.getName(),
                recipient.getCountryCode(),
                recipient.getCurrencyCode(),
                recipient.getBankName(),
                recipient.getAccountNumber(),
                remittanceTransaction.getMethod(),
                remittanceTransaction.getSourceMockAccountId(),
                remittanceTransaction.getTargetMockAccountId(),
                remittanceTransaction.getSendCurrency(),
                remittanceTransaction.getSendAmount(),
                remittanceTransaction.getReceiveCurrency(),
                remittanceTransaction.getReceiveAmount(),
                remittanceTransaction.getAppliedRate(),
                remittanceTransaction.getFeeAmount(),
                remittanceTransaction.getAmountKrw(),
                remittanceTransaction.getAmountUsd(),
                remittanceTransaction.getReason(),
                remittanceTransaction.getReasonDetail(),
                remittanceTransaction.getStatus(),
                remittanceTransaction.getFailureReason(),
                remittanceTransaction.getCreatedAt(),
                remittanceTransaction.getUpdatedAt()
        );
    }
}
