package com.fxflow.domain.companypool.dto.response;

import com.fxflow.domain.companypool.entity.RebalancingOrder;
import com.fxflow.domain.companypool.enums.TriggerType;

import java.math.BigDecimal;

public record RebalancingExecuteRes(
        boolean executed,
        RebalancingAction action,
        String cappedBy,
        String reason
) {
    //실행된 리밸런싱 상세 정보
    public record RebalancingAction(
            String buyCurrency,
            String sellCurrency,
            BigDecimal buyAmount,
            BigDecimal sellAmount,
            BigDecimal midRate,
            BigDecimal appliedRate,
            String status,
            TriggerType triggerType
    ) {}

    public static RebalancingExecuteRes withinThreshold() {
        return new RebalancingExecuteRes(false, null, null, "WITHIN_THRESHOLD");
    }

    public static RebalancingExecuteRes bothBelowFloor() {
        return new RebalancingExecuteRes(false, null, null, "BOTH_BELOW_FLOOR");
    }

    public static RebalancingExecuteRes from(RebalancingOrder order) {
        return new RebalancingExecuteRes(
                true,
                new RebalancingAction(
                        order.getBuyPool().getCurrencyCode(),
                        order.getSellPool().getCurrencyCode(),
                        order.getBuyAmount(),
                        order.getSellAmount(),
                        order.getMidRate(),
                        order.getAppliedRate(),
                        order.getStatus().name(),
                        order.getTriggerType()
                ),
                order.getCappedBy() != null ? order.getCappedBy().name() : null,
                null
        );
    }
}