package com.fxflow.domain.companypool.dto.response;

import com.fxflow.domain.companypool.entity.RebalancingOrder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RebalancingHistoryRes(
        Long id,
        String buyCurrency,
        BigDecimal buyAmount,
        String sellCurrency,
        BigDecimal sellAmount,
        BigDecimal midRate,
        BigDecimal appliedRate,
        String status,
        String cappedBy,
        String triggerType,
        String reason,
        LocalDateTime executedAt
) {
    public static RebalancingHistoryRes from(RebalancingOrder order) {
        return new RebalancingHistoryRes(
                order.getId(),
                order.getBuyPool() != null ? order.getBuyPool().getCurrencyCode() : null,
                order.getBuyAmount(),
                order.getSellPool() != null ? order.getSellPool().getCurrencyCode() : null,
                order.getSellAmount(),
                order.getMidRate(),
                order.getAppliedRate(),
                order.getStatus().name(),
                order.getCappedBy() != null ? order.getCappedBy().name() : null,
                order.getTriggerType().name(),
                order.getReason(),
                order.getCreatedAt()
        );
    }
}