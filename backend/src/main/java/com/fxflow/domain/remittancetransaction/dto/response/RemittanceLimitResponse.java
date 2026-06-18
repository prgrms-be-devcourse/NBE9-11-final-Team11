package com.fxflow.domain.remittancetransaction.dto.response;

import com.fxflow.global.util.CurrencyAmountFormatter;

import java.math.BigDecimal;

public record RemittanceLimitResponse(
        Long userId,
        BigDecimal maxPerTransactionUsd,
        BigDecimal maxPerYearUsd,
        BigDecimal currentYearTotalUsd,
        BigDecimal availableYearUsd
) {

    private static final String USD = "USD";

    public static RemittanceLimitResponse of(
            Long userId,
            BigDecimal maxPerTransactionUsd,
            BigDecimal maxPerYearUsd,
            BigDecimal currentYearTotalUsd
    ) {
        BigDecimal availableYearUsd = maxPerYearUsd
                .subtract(currentYearTotalUsd)
                .max(BigDecimal.ZERO);

        return new RemittanceLimitResponse(
                userId,
                CurrencyAmountFormatter.format(maxPerTransactionUsd, USD),
                CurrencyAmountFormatter.format(maxPerYearUsd, USD),
                CurrencyAmountFormatter.format(currentYearTotalUsd, USD),
                CurrencyAmountFormatter.format(availableYearUsd, USD)
        );
    }
}
