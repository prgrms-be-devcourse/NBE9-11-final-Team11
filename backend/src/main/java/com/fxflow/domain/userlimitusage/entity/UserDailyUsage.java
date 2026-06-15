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
        name = "user_daily_usages",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_date",
                columnNames = {"user_id", "usage_date"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserDailyUsage extends BaseEntity {

    /**
     * 사용자 FK
     * LAZY 로딩
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    //기준 날짜
    @Column(name = "usage_date", nullable = false)
    private LocalDate usageDate;

    /**
     * 일일 입금 누적액 (KRW 기준)
     */
    @Column(name = "daily_deposit_used", nullable = false, precision = 18, scale = 8)
    private BigDecimal dailyDepositUsed;

    /**
     * 일일 출금 누적액 (KRW 기준)
     */
    @Column(name = "daily_withdrawal_used", nullable = false, precision = 18, scale = 8)
    private BigDecimal dailyWithdrawalUsed;

    /**
     * 낙관적 락 버전
     * 동시 입출금 요청 시 누적액 중복 반영 방지
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    // ── 생성 ────────────────────────────────────────────────────────────────

    public static UserDailyUsage create(User user, LocalDate usageDate) {
        UserDailyUsage usage = new UserDailyUsage();
        usage.user = user;
        usage.usageDate = usageDate;
        usage.dailyDepositUsed = BigDecimal.ZERO;
        usage.dailyWithdrawalUsed = BigDecimal.ZERO;
        return usage;
    }

    // ── 비즈니스 메서드 ──────────────────────────────────────────────────────

    /** 입금 완료 시 일일 누적액 추가 */
    public void addDeposit(BigDecimal amount) {
        this.dailyDepositUsed = this.dailyDepositUsed.add(amount);
    }

    /** 출금 완료 시 일일 누적액 추가 */
    public void addWithdrawal(BigDecimal amount) {
        this.dailyWithdrawalUsed = this.dailyWithdrawalUsed.add(amount);
    }
}