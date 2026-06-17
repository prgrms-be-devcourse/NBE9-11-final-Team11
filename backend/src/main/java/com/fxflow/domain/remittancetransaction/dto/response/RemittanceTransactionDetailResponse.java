package com.fxflow.domain.remittancetransaction.dto.response;

import com.fxflow.domain.remittancetransaction.entity.RemittanceTransaction;
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
     * 수취인 정보는 주소록 최신값이 아니라 송금 당시 저장된 스냅샷을 사용한다.
     */
    public static RemittanceTransactionDetailResponse from(RemittanceTransaction remittanceTransaction) {
        return new RemittanceTransactionDetailResponse(
                remittanceTransaction.getId(),
                remittanceTransaction.getRecipientId(),
                remittanceTransaction.getRecipientName(),
                remittanceTransaction.getRecipientCountryCode(),
                remittanceTransaction.getRecipientCurrencyCode(),
                remittanceTransaction.getRecipientBankName(),
                remittanceTransaction.getRecipientAccountNumber(),
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