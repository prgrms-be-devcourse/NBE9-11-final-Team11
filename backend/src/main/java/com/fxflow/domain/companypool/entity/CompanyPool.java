package com.fxflow.domain.companypool.entity;

import com.fxflow.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "company_pools")
public class CompanyPool extends BaseEntity {

    @Column(name = "currency_code", unique = true, nullable = false, length = 3)
    private String currencyCode;

    @Column(nullable = false, precision = 23, scale = 8)
    private BigDecimal balance;

    @Column(nullable = false, precision = 23, scale = 8)
    private BigDecimal targetBalance;

    @Column(nullable = false, precision = 23, scale = 8)
    private BigDecimal floorBalance;

    // 리밸런싱 경계: 매입 시 채울 목표 & 매도 시 내릴 수 있는 하한 (= target의 80%)
    @Column(precision = 23, scale = 8)
    private BigDecimal safeFloorBalance;

    @Column(nullable = false, precision = 23, scale = 8)
    private BigDecimal ceilingBalance;

    // 상태 판단
    public boolean isBelowFloor() {
        return balance.compareTo(floorBalance) < 0;
    }
    public boolean isAboveCeiling() {
        return balance.compareTo(ceilingBalance) > 0;
    }
    public boolean isWithinThreshold() {
        return !isBelowFloor() && !isAboveCeiling();
    }

    // 금액 계산
    /** safeFloor까지 부족한 금액 — 리밸런싱 시 매입 목표량 */
    public BigDecimal shortageToSafeFloor() {
        BigDecimal safe = effectiveSafeFloor();
        BigDecimal shortage = safe.subtract(balance);
        return shortage.compareTo(BigDecimal.ZERO) > 0 ? shortage : BigDecimal.ZERO;
    }
    /** safeFloor까지 내줄 수 있는 여유분 — 리밸런싱 시 매도 가능 최대량 */
    public BigDecimal surplusAboveSafeFloor() {
        BigDecimal safe = effectiveSafeFloor();
        BigDecimal surplus = balance.subtract(safe);
        return surplus.compareTo(BigDecimal.ZERO) > 0 ? surplus : BigDecimal.ZERO;
    }

    // DB 마이그레이션 전 기존 행에 safe_floor_balance가 NULL일 수 있음 — target의 80%로 대체
    public BigDecimal effectiveSafeFloor() {
        if (safeFloorBalance != null) return safeFloorBalance;
        return targetBalance.multiply(new BigDecimal("0.8")).setScale(8, java.math.RoundingMode.FLOOR);
    }

    // 잔액 변경
    public void increase(BigDecimal amount) {
        this.balance = this.balance.add(amount);
    }
    public void decrease(BigDecimal amount) {
        this.balance = this.balance.subtract(amount);
    }
}
