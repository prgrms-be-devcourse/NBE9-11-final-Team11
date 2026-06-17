package com.fxflow.domain.userlimitusage.entity;

import com.fxflow.domain.user.entity.User;
import com.fxflow.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(
        name = "user_exchange_annual_usages",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_exchange_year",
                columnNames = {"user_id", "year"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserExchangeAnnualUsage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "year", nullable = false)
    private Integer year;

    /**
     * 연간 환전 누적액 (USD 기준)
     */
    @Column(name = "annual_exchange_used_usd", nullable = false, precision = 18, scale = 8)
    private BigDecimal annualExchangeUsedUsd;

    /**
     * 낙관적 락 버전
     * 동시 환전 요청 시 누적액 중복 반영 방지
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public static UserExchangeAnnualUsage create(User user, Integer year) {
        UserExchangeAnnualUsage usage = new UserExchangeAnnualUsage();
        usage.user = user;
        usage.year = year;
        usage.annualExchangeUsedUsd = BigDecimal.ZERO;
        return usage;
    }

    /** 환전 완료 시 연간 누적액 추가 */
    public void addExchange(BigDecimal amount) {
        this.annualExchangeUsedUsd = this.annualExchangeUsedUsd.add(amount);
    }
}