package com.fxflow.domain.companypool.dto;

import java.math.BigDecimal;

public record PoolChange(String currencyCode, BigDecimal amount, boolean isIncrease) {

    public static PoolChange increase(String currencyCode, BigDecimal amount) {
        return new PoolChange(currencyCode, amount, true);
    }

    public static PoolChange decrease(String currencyCode, BigDecimal amount) {
        return new PoolChange(currencyCode, amount, false);
    }
}
