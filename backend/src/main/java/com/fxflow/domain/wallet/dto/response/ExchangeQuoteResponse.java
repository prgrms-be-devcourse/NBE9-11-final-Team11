package com.fxflow.domain.wallet.dto.response;

import com.fxflow.global.util.CurrencyAmountFormatter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ExchangeQuoteResponse(
        String fromCurrency,
        String toCurrency,
        BigDecimal fromAmount,
        BigDecimal toAmount,  // 수수료 적용 전, 환율만 적용된 순수 환전 금액
        BigDecimal exchangeRate,
        BigDecimal fee,
        BigDecimal totalAmount,  // 실제로 사용자가 받게 될 — 또는 차감될 — 최종 금액, 수수료 포함
        LocalDateTime expiredAt,
        String quoteId
) {
    public static ExchangeQuoteResponse from(
            String fromCurrency, String toCurrency,
            BigDecimal fromAmount, BigDecimal toAmount,
            BigDecimal exchangeRate, BigDecimal feeAmount,
            BigDecimal totalAmount, LocalDateTime expiredAt,
            String quoteId
    ) {
        return new ExchangeQuoteResponse(
                fromCurrency,
                toCurrency,
                CurrencyAmountFormatter.format(fromAmount, fromCurrency),
                CurrencyAmountFormatter.format(toAmount, toCurrency),
                exchangeRate,
                CurrencyAmountFormatter.format(feeAmount, toCurrency), // fee는 toCurrency 단위
                CurrencyAmountFormatter.format(totalAmount, toCurrency),
                expiredAt,
                quoteId
        );
    }
}
