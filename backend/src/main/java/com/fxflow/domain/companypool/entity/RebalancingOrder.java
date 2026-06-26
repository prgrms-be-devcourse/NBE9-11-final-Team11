package com.fxflow.domain.companypool.entity;

import com.fxflow.domain.companypool.enums.CappedBy;
import com.fxflow.domain.companypool.enums.RebalancingStatus;
import com.fxflow.domain.companypool.enums.TriggerType;
import com.fxflow.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "rebalancing_orders")
public class RebalancingOrder extends BaseEntity {

    // MANUAL_REQUIRED(양 통화 모두 floor 미만) 케이스는 nullable
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buy_pool_id")
    private CompanyPool buyPool;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sell_pool_id")
    private CompanyPool sellPool;

    @Column(precision = 18, scale = 8)
    private BigDecimal buyAmount;

    @Column(precision = 18, scale = 8)
    private BigDecimal sellAmount;

    @Column(precision = 18, scale = 8)
    private BigDecimal buyBalanceBefore;

    @Column(precision = 18, scale = 8)
    private BigDecimal sellBalanceBefore;

    @Column(precision = 18, scale = 8)
    private BigDecimal midRate;

    @Column(precision = 18, scale = 8)
    private BigDecimal appliedRate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RebalancingStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private CappedBy cappedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TriggerType triggerType;

    @Column(length = 255)
    private String reason;

    @Column(nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    private RebalancingOrder(CompanyPool buyPool, CompanyPool sellPool,
                             BigDecimal buyAmount, BigDecimal sellAmount,
                             BigDecimal buyBalanceBefore, BigDecimal sellBalanceBefore,
                             BigDecimal midRate, BigDecimal appliedRate,
                             RebalancingStatus status, CappedBy cappedBy,
                             TriggerType triggerType, String reason,
                             String idempotencyKey) {
        this.buyPool = buyPool;
        this.sellPool = sellPool;
        this.buyAmount = buyAmount;
        this.sellAmount = sellAmount;
        this.buyBalanceBefore = buyBalanceBefore;
        this.sellBalanceBefore = sellBalanceBefore;
        this.midRate = midRate;
        this.appliedRate = appliedRate;
        this.status = status;
        this.cappedBy = cappedBy;
        this.triggerType = triggerType;
        this.reason = reason;
        this.idempotencyKey = idempotencyKey;
    }

    public static RebalancingOrder create(CompanyPool buyPool, CompanyPool sellPool,
                                          BigDecimal buyAmount, BigDecimal sellAmount,
                                          BigDecimal buyBalanceBefore, BigDecimal sellBalanceBefore,
                                          BigDecimal midRate, BigDecimal appliedRate,
                                          RebalancingStatus status, CappedBy cappedBy,
                                          TriggerType triggerType, String reason,
                                          String idempotencyKey) {
        return new RebalancingOrder(buyPool, sellPool, buyAmount, sellAmount,
                buyBalanceBefore, sellBalanceBefore, midRate, appliedRate,
                status, cappedBy, triggerType, reason, idempotencyKey);
    }

    // 양 통화 모두 floor 미만 — 거래 없이 알람 기록용
    public static RebalancingOrder createAlert(TriggerType triggerType, String reason,
                                               String idempotencyKey) {
        return new RebalancingOrder(null, null, null, null, null, null, null, null,
                RebalancingStatus.MANUAL_REQUIRED, null, triggerType, reason, idempotencyKey);
    }
}