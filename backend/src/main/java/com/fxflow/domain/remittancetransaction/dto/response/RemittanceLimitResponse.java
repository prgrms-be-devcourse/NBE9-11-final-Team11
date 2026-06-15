package com.fxflow.domain.remittancetransaction.dto.response;

import java.math.BigDecimal;

public record RemittanceLimitResponse(
        Long userId,
        BigDecimal maxPerTransactionUsd,
        BigDecimal maxPerYearUsd,
        BigDecimal currentYearTotalUsd,
        BigDecimal availableYearUsd
) {

    public static RemittanceLimitResponse of(Long userId, BigDecimal currentYearTotalUsd, BigDecimal availableYearUsd) {
        return new RemittanceLimitResponse(
                userId,
                new BigDecimal("5000.00"),
                new BigDecimal("100000.00"),
                currentYearTotalUsd,
                availableYearUsd
        );
    }
}