package com.fxflow.domain.userlimitusage.entity;

import com.fxflow.domain.user.entity.User;
import com.fxflow.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(
        name = "user_exchange_daily_usages",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_exchange_date",
                columnNames = {"user_id", "usage_date"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserExchangeDailyUsage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "usage_date", nullable = false)
    private LocalDate usageDate;

    /**
     * 일일 환전 누적액 (KRW 기준)
     */
    @Column(name = "daily_exchange_used", nullable = false, precision = 18, scale = 8)
    private BigDecimal dailyExchangeUsed;

    /**
     * 낙관적 락 버전
     * 동시 환전 요청 시 누적액 중복 반영 방지
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public static UserExchangeDailyUsage create(User user, LocalDate usageDate) {
        UserExchangeDailyUsage usage = new UserExchangeDailyUsage();
        usage.user = user;
        usage.usageDate = usageDate;
        usage.dailyExchangeUsed = BigDecimal.ZERO;
        return usage;
    }

    /** 환전 완료 시 일일 누적액 추가 */
    public void addExchange(BigDecimal amount) {
        this.dailyExchangeUsed = this.dailyExchangeUsed.add(amount);
    }
}