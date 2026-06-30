package com.fxflow.domain.wallet.dto.response;

import com.fxflow.domain.wallet.entity.ExchangeTransaction;
import com.fxflow.global.util.CurrencyAmountFormatter;

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
                CurrencyAmountFormatter.format(exchangeTransaction.getFromAmount(), exchangeTransaction.getFromCurrencyCode()),
                exchangeTransaction.getToCurrencyCode(),
                CurrencyAmountFormatter.format(exchangeTransaction.getToAmount(), exchangeTransaction.getToCurrencyCode()),
                exchangeTransaction.getFinalRate(),
                CurrencyAmountFormatter.format(exchangeTransaction.getFeeAmount(), exchangeTransaction.getFromCurrencyCode()),
                exchangeTransaction.getCreatedAt()
        );
    }
}
