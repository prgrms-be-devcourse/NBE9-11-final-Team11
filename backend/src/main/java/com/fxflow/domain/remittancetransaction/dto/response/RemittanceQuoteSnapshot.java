package com.fxflow.domain.remittancetransaction.dto.response;

import java.math.BigDecimal;

public record RemittanceQuoteSnapshot(
        String quoteId,
        Long recipientId,
        String sendCurrency,
        BigDecimal sendAmount,
        String receiveCurrency,
        BigDecimal receiveAmount,
        BigDecimal appliedRate,
        BigDecimal feeAmount,
        BigDecimal amountKrw,
        BigDecimal amountUsd
) {

    public BigDecimal totalPaymentAmount() {
        return sendAmount.add(feeAmount);
    }
}