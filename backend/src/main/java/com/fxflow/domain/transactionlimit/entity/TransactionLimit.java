package com.fxflow.domain.transactionlimit.entity;

import com.fxflow.domain.transactionlimit.enums.LimitTier;
import com.fxflow.domain.transactionlimit.enums.LimitType;
import com.fxflow.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(
        name = "transaction_limits",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_limit_type_tier_currency",
                columnNames = {"limit_type", "tier", "currency_code"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransactionLimit extends BaseEntity {

    /**
     * 한도 유형
     * PER_REMITTANCE       - 건당 송금 한도        (USD $5,000)
     * ANNUAL_REMITTANCE    - 연간 누적 송금 한도    (USD $100,000)
     * PER_DEPOSIT          - 1회 입금 한도          (KRW 200만/300만)
     * DAILY_DEPOSIT        - 일일 입금 한도         (KRW 200만/300만)
     * PER_WITHDRAWAL       - 1회 출금 한도          (KRW 200만/300만)
     * DAILY_WITHDRAWAL     - 일일 출금 한도         (KRW 200만/300만)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "limit_type", nullable = false, length = 30)
    private LimitType limitType;

    /**
     * 한도 등급
     * STANDARD - 기본 회원 한도
     * ENHANCED - 증액 신청 완료 회원 한도
     *
     * PER_REMITTANCE, ANNUAL_REMITTANCE 는 증액 대상이 아니므로 항상 STANDARD
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "tier", nullable = false, length = 20)
    private LimitTier tier;

    /**
     * 한도 기준 통화 코드
     * 송금 한도 → USD
     * 입출금 한도 → KRW
     */
    @Column(name = "currency_code", nullable = false, length = 10)
    private String currencyCode;

    /**
     * 한도 금액
     * 송금 한도는 USD 기준, 입출금 한도는 KRW 기준
     */
    @Column(name = "limit_amount", nullable = false, precision = 18, scale = 8)
    private BigDecimal limitAmount;

    /**
     * 정책 활성화 여부
     * false 시 해당 한도 정책은 Validator에서 무시됨
     */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    // ── 생성 ───────────────────────────────────────────────────────────────

    /**
     * 외부에서 new 키워드 대신 이 메서드로만 생성
     * 생성 시 isActive 기본값 true 보장
     */
    public static TransactionLimit create(
            LimitType limitType,
            LimitTier tier,
            String currencyCode,
            BigDecimal limitAmount
    ) {
        TransactionLimit limit = new TransactionLimit();
        limit.limitType = limitType;
        limit.tier = tier;
        limit.currencyCode = currencyCode;
        limit.limitAmount = limitAmount;
        limit.isActive = true;
        return limit;
    }

    // ── 관리자용 상태 변경 ─────────────────────────────────────────────────

    /** 한도 정책 활성화 */
    public void activate() { this.isActive = true; }

    /** 한도 정책 비활성화 — Validator에서 해당 정책 무시됨 */
    public void deactivate() { this.isActive = false; }

    /** 한도 금액 수정 — 기획 변경 시 관리자가 호출 */
    public void updateLimitAmount(BigDecimal newAmount) { this.limitAmount = newAmount; }
}