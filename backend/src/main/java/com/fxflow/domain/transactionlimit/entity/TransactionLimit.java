package com.fxflow.domain.transactionlimit.entity;

import com.fxflow.domain.transactionlimit.enums.LimitType;
import com.fxflow.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "transaction_limits")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransactionLimit extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "limit_type", nullable = false, length = 30)
    private LimitType limitType;        // PER_REMITTANCE / ANNUAL_REMITTANCE

    @Column(name = "currency_code", nullable = false, length = 10)
    private String currencyCode;        // USD

    @Column(name = "limit_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal limitAmount;     // 5000 / 100000

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;           // 활성 여부
}