package com.fxflow.domain.remittancetransaction.dto.response;

import com.fxflow.domain.remittancetransaction.entity.RemittanceTransaction;
import com.fxflow.domain.remittancetransaction.entity.Recipient;
import com.fxflow.domain.remittancetransaction.enums.TransferStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RemittanceTransactionSummaryResponse(
        Long transferId,
        String recipientName,
        String recipientCountryCode,
        String recipientCurrencyCode,
        String recipientBankName,
        BigDecimal sendAmount,
        String sendCurrency,
        BigDecimal receiveAmount,
        String receiveCurrency,
        BigDecimal feeAmount,
        TransferStatus status,
        LocalDateTime createdAt
) {

    /**
     * 송금 내역 목록에 필요한 요약 정보를 생성한다.
     * 수취인 정보는 recipientId로 연결된 수취인 주소록에서 조회한다.
     */
    public static RemittanceTransactionSummaryResponse from(
            RemittanceTransaction remittanceTransaction,
            Recipient recipient
    ) {
        return new RemittanceTransactionSummaryResponse(
                remittanceTransaction.getId(),
                recipient.getName(),
                recipient.getCountryCode(),
                recipient.getCurrencyCode(),
                recipient.getBankName(),
                remittanceTransaction.getSendAmount(),
                remittanceTransaction.getSendCurrency(),
                remittanceTransaction.getReceiveAmount(),
                remittanceTransaction.getReceiveCurrency(),
                remittanceTransaction.getFeeAmount(),
                remittanceTransaction.getStatus(),
                remittanceTransaction.getCreatedAt()
        );
    }
}
