package com.fxflow.domain.companypool;

import java.math.BigDecimal;

public final class PoolTestFixtures {

    private PoolTestFixtures() {}

    public static final BigDecimal KRW_TARGET     = new BigDecimal("10000000000");
    public static final BigDecimal KRW_SAFE_FLOOR = new BigDecimal("8000000000");  // 80% — 리밸런싱 경계
    public static final BigDecimal KRW_FLOOR      = new BigDecimal("6000000000");  // 60% — trigger 임계값
    public static final BigDecimal KRW_CEILING    = new BigDecimal("12000000000");

    public static final BigDecimal USD_TARGET     = new BigDecimal("6500000");
    public static final BigDecimal USD_SAFE_FLOOR = new BigDecimal("5200000");     // 80%
    public static final BigDecimal USD_FLOOR      = new BigDecimal("3900000");     // 60%
    public static final BigDecimal USD_CEILING    = new BigDecimal("7800000");

    public static final BigDecimal MID_RATE = new BigDecimal("1300");
}
