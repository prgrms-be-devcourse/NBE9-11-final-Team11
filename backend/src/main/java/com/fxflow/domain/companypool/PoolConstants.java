package com.fxflow.domain.companypool;

import java.math.BigDecimal;
import java.time.Duration;

public final class PoolConstants {

    public static final BigDecimal SPREAD = new BigDecimal("0.002");

    // 마지막 성공 리밸런싱 이후 자동 재실행을 막는 쿨다운 기간
    public static final Duration REBALANCING_COOLDOWN = Duration.ofMinutes(30);

    private PoolConstants() {}
}
