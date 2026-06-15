package com.fxflow.domain.fxrate.entity;

import com.fxflow.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "fx_rates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FxRate extends BaseEntity {

    @Column(name = "base_currency", length = 10, nullable = false)
    private String baseCurrency; // 기준 통화

    @Column(name = "quote_currency", length = 10, nullable = false)
    private String quoteCurrency; // 상대 통화

    @Column(name = "mid_rate", precision = 18, scale = 8, nullable = false)
    private BigDecimal midRate; // 기준 환율

    @Column(name = "spread", precision = 18, scale = 8, nullable = false)
    private BigDecimal spread; // 스프레드

    @Column(name = "applied_rate", precision = 18, scale = 8, nullable = false)
    private BigDecimal appliedRate; // 적용 환율

    @Column(name = "source", length = 50, nullable = false)
    private String source; // 출처

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt; // 수집 시각

    private FxRate(String baseCurrency, String quoteCurrency, BigDecimal midRate,
                    BigDecimal spread, BigDecimal appliedRate, String source, LocalDateTime fetchedAt) {
        this.baseCurrency = baseCurrency;
        this.quoteCurrency = quoteCurrency;
        this.midRate = midRate;
        this.spread = spread;
        this.appliedRate = appliedRate;
        this.source = source;
        this.fetchedAt = fetchedAt;
    }

    // 필수값 누락 및 잘못된 상태의 객체 생성을 막기 위해 빌더 대신 정적 팩토리 메서드 사용
    public static FxRate create(String baseCurrency, String quoteCurrency, BigDecimal midRate,
                                 BigDecimal spread, BigDecimal appliedRate, String source, LocalDateTime fetchedAt) {
        return new FxRate(baseCurrency, quoteCurrency, midRate, spread, appliedRate, source, fetchedAt);
    }
}
