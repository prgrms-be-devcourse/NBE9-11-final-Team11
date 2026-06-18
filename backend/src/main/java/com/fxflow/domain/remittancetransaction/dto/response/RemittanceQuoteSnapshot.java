package com.fxflow.domain.remittancetransaction.dto.response;

import com.fxflow.domain.remittancetransaction.dto.cache.RemittanceQuoteCache;

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

    public static RemittanceQuoteSnapshot from(String quoteId, RemittanceQuoteCache cache) {
        return new RemittanceQuoteSnapshot(
                quoteId,
                cache.recipientId(),
                cache.sendCurrency(),
                cache.sendAmount(),
                cache.receiveCurrency(),
                cache.receiveAmount(),
                cache.appliedRate(),
                cache.feeAmount(),
                cache.amountKrw(),
                cache.amountUsd()
        );
    }

    public BigDecimal totalPaymentAmount() {
        return sendAmount.add(feeAmount);
    }
}