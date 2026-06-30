package com.fxflow.domain.companypool.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record PoolDashboardRes(
        OffsetDateTime asOf,
        List<PoolStatusRes> pools
) {
    public record PoolStatusRes(
            String currencyCode,
            BigDecimal balance,
            BigDecimal targetBalance,
            BigDecimal floorBalance,
            BigDecimal safeFloorBalance,
            BigDecimal ceilingBalance,
            String status,
            BigDecimal utilizationRate,
            RecommendedAction recommendedAction
    ) {}

    // null이면 정상 범위. counterAmount는 환율 조회 실패 시 null
    public record RecommendedAction(String type, BigDecimal amount, BigDecimal counterAmount) {}
}