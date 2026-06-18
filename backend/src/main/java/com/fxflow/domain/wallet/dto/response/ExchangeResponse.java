package com.fxflow.domain.wallet.dto.response;

import com.fxflow.domain.wallet.entity.ExchangeTransaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ExchangeResponse (
        String transactionId,
        String fromCurrency,
        BigDecimal fromAmount,
        String toCurrency,
        BigDecimal toAmount,
        BigDecimal appliedRate,
        BigDecimal fee,
        LocalDateTime executedAt
){
    public static ExchangeResponse from(ExchangeTransaction exchangeTransaction) {
        return new ExchangeResponse(
                exchangeTransaction.getTransactionId(),
                exchangeTransaction.getFromCurrencyCode(),
                exchangeTransaction.getFromAmount(),
                exchangeTransaction.getToCurrencyCode(),
                exchangeTransaction.getToAmount(),
                exchangeTransaction.getFinalRate(),
                exchangeTransaction.getFeeAmount(),
                exchangeTransaction.getCreatedAt()
        );
    }
}
