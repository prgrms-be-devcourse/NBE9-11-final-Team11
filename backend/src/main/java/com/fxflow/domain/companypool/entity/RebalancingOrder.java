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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buy_pool_id", nullable = false)
    private CompanyPool buyPool;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sell_pool_id", nullable = false)
    private CompanyPool sellPool;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal buyAmount;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal sellAmount;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal buyBalanceBefore;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal sellBalanceBefore;

    @Column(nullable = false, precision = 18, scale = 8)
    private BigDecimal midRate;

    @Column(nullable = false, precision = 18, scale = 8)
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
}