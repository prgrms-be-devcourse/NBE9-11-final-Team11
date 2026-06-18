package com.fxflow.domain.remittancetransaction.dto.cache;

import java.io.Serializable;
import java.math.BigDecimal;

public record RemittanceQuoteCache(
        Long userId,
        Long recipientId,
        String sendCurrency,
        BigDecimal sendAmount,
        String receiveCurrency,
        BigDecimal receiveAmount,
        BigDecimal appliedRate,
        BigDecimal feeAmount,
        BigDecimal amountKrw,
        BigDecimal amountUsd
) implements Serializable {
}