package com.fxflow.domain.remittancetransaction.dto.response;

import java.math.BigDecimal;

public record RemittanceLimitResponse(
        Long userId,
        BigDecimal maxPerTransactionUsd,
        BigDecimal maxPerYearUsd,
        BigDecimal currentYearTotalUsd,
        BigDecimal availableYearUsd
) {

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
                maxPerTransactionUsd,
                maxPerYearUsd,
                currentYearTotalUsd,
                availableYearUsd
        );
    }
}