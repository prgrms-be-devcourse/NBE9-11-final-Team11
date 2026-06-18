package com.fxflow.domain.companypool.dto.response;

import com.fxflow.domain.companypool.entity.RebalancingOrder;
import com.fxflow.domain.companypool.enums.TriggerType;

import java.math.BigDecimal;

public record RebalancingExecuteRes(
        boolean executed,
        String buyCurrency,
        BigDecimal buyAmount,
        String sellCurrency,
        BigDecimal sellAmount,
        BigDecimal midRate,
        BigDecimal appliedRate,
        String cappedBy,
        String status,
        TriggerType triggerType
) {
    public static RebalancingExecuteRes withinThreshold() {
        return new RebalancingExecuteRes(false, null, null, null, null, null, null, null, null, null);
    }

    public static RebalancingExecuteRes from(RebalancingOrder order) {
        return new RebalancingExecuteRes(
                true,
                order.getBuyPool().getCurrencyCode(),
                order.getBuyAmount(),
                order.getSellPool().getCurrencyCode(),
                order.getSellAmount(),
                order.getMidRate(),
                order.getAppliedRate(),
                order.getCappedBy() != null ? order.getCappedBy().name() : null,
                order.getStatus().name(),
                order.getTriggerType()
        );
    }
}