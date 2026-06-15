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
        name = "user_limit_usages",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_year",
                columnNames = {"user_id", "year"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserLimitUsage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "year", nullable = false)
    private Integer year;

    /**
     * 연간 송금 누적액 (USD 기준)
     * 송금 완료 시마다 누적, 연간 한도 $100,000 검증에 사용
     */
    @Column(name = "annual_used_usd", nullable = false, precision = 18, scale = 2)
    private BigDecimal annualUsedUsd;

    /**
     * 낙관적 락 버전
     * 동시 송금 요청 시 누적액 중복 반영 방지
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public static UserLimitUsage create(User user, Integer year) {
        UserLimitUsage usage = new UserLimitUsage();
        usage.user = user;
        usage.year = year;
        usage.annualUsedUsd = BigDecimal.ZERO;
        return usage;
    }
    /**
     * 송금 완료 시 누적액 추가
     */
    public void addUsage(BigDecimal amount) {
        this.annualUsedUsd = this.annualUsedUsd.add(amount);
    }

    /**
     * 송금 취소/실패 시 누적액 차감
     */
    public void subtractUsage(BigDecimal amount) {
        this.annualUsedUsd = this.annualUsedUsd.subtract(amount);
    }
}