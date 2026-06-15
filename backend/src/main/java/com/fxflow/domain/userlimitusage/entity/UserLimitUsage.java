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
        name = "user_limit_usages",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_year_date",
                columnNames = {"user_id", "year", "usage_date"}
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
    @Column(name = "annual_used_usd", nullable = false, precision = 18, scale = 8)
    private BigDecimal annualUsedUsd;

    /**
     * 기준 날짜 (KST 기준)
     * 일별 입출금 한도 검증에 사용
     */
    @Column(name = "usage_date", nullable = false)
    private LocalDate usageDate;

    /**
     * 일일 입금 누적액 (KRW 기준)
     * 자정 기준으로 초기화
     */
    @Column(name = "daily_deposit_used", nullable = false, precision = 18, scale = 8)
    private BigDecimal dailyDepositUsed;

    /**
     * 일일 출금 누적액 (KRW 기준)
     * 자정 기준으로 초기화
     */
    @Column(name = "daily_withdrawal_used", nullable = false, precision = 18, scale = 8)
    private BigDecimal dailyWithdrawalUsed;

    /**
     * 낙관적 락 버전
     * 동시 요청 시 누적액 중복 반영 방지
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    // ── 생성 ────────────────────────────────────────────────────────────────

    public static UserLimitUsage create(User user, Integer year, LocalDate usageDate) {
        UserLimitUsage usage = new UserLimitUsage();
        usage.user = user;
        usage.year = year;
        usage.usageDate = usageDate;
        usage.annualUsedUsd = BigDecimal.ZERO;
        usage.dailyDepositUsed = BigDecimal.ZERO;
        usage.dailyWithdrawalUsed = BigDecimal.ZERO;
        return usage;
    }

    // ── 연간 송금 누적 ───────────────────────────────────────────────────────

    /** 송금 완료 시 연간 누적액 추가 */
    public void addAnnualUsage(BigDecimal amount) {
        this.annualUsedUsd = this.annualUsedUsd.add(amount);
    }

    /** 송금 취소/실패 시 연간 누적액 차감 */
    public void subtractAnnualUsage(BigDecimal amount) {
        this.annualUsedUsd = this.annualUsedUsd.subtract(amount);
    }

    // ── 일일 입출금 누적 ─────────────────────────────────────────────────────

    /** 입금 완료 시 일일 누적액 추가 */
    public void addDailyDeposit(BigDecimal amount) {
        this.dailyDepositUsed = this.dailyDepositUsed.add(amount);
    }

    /** 출금 완료 시 일일 누적액 추가 */
    public void addDailyWithdrawal(BigDecimal amount) {
        this.dailyWithdrawalUsed = this.dailyWithdrawalUsed.add(amount);
    }
}