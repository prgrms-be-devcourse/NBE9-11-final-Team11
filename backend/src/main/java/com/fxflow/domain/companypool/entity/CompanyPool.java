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

    @Column(nullable = false, precision = 18, scale = 8)
    private BigDecimal balance;

    @Column(nullable = false, precision = 18, scale = 8)
    private BigDecimal targetBalance;

    @Column(nullable = false, precision = 18, scale = 8)
    private BigDecimal floorBalance;

    @Column(nullable = false, precision = 18, scale = 8)
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
    /** target까지 부족한 금액 (floor 미만일 때 매입해야 할 수량의 기준) */
    public BigDecimal shortageToTarget() {
        BigDecimal shortage = targetBalance.subtract(balance);
        return shortage.compareTo(BigDecimal.ZERO) > 0 ? shortage : BigDecimal.ZERO;
    }
    /** floor까지 내줄 수 있는 여유분 (반대쪽 매입을 위해 매도 가능한 최대 수량) */
    public BigDecimal surplusAboveFloor() {
        BigDecimal surplus = balance.subtract(floorBalance);
        return surplus.compareTo(BigDecimal.ZERO) > 0 ? surplus : BigDecimal.ZERO;
    }

    // 잔액 변경
    public void increase(BigDecimal amount) {
        this.balance = this.balance.add(amount);
    }
    public void decrease(BigDecimal amount) {
        this.balance = this.balance.subtract(amount);
    }
}
