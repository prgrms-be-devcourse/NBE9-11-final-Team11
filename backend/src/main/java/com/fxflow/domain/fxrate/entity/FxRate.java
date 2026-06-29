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
    private BigDecimal spread; // 스프레드 (비율형, 적용 환율은 mid×(1±spread)로 파생)

    // 플랫폼 FX 마진 정책 — 현재 스프레드 0 (mid=buy=sell). 정책 확정 시 이 값 변경
    private static final BigDecimal DEFAULT_SPREAD = BigDecimal.ZERO;

    @Column(name = "source", length = 50, nullable = false)
    private String source; // 출처

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt; // 수집 시각

    private FxRate(String baseCurrency, String quoteCurrency, BigDecimal midRate,
                    String source, LocalDateTime fetchedAt) {
        // 잘못된 상태의 객체 생성을 막기 위한 필수값 검증 (금융 도메인 불변식)
        if (baseCurrency == null || baseCurrency.isBlank()) {
            throw new IllegalArgumentException("Base currency must not be null or blank");
        }
        if (quoteCurrency == null || quoteCurrency.isBlank()) {
            throw new IllegalArgumentException("Quote currency must not be null or blank");
        }
        // 환율은 0 이하일 수 없다 (0이면 KRW↔USD 변환 시 0으로 나누기 발생)
        if (midRate == null || midRate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Mid rate must be positive");
        }
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("Source must not be null or blank");
        }
        if (fetchedAt == null) {
            throw new IllegalArgumentException("Fetched at must not be null");
        }
        this.baseCurrency = baseCurrency;
        this.quoteCurrency = quoteCurrency;
        this.midRate = midRate;
        this.spread = DEFAULT_SPREAD;
        this.source = source;
        this.fetchedAt = fetchedAt;
    }

    // 필수값 누락 및 잘못된 상태의 객체 생성을 막기 위해 빌더 대신 정적 팩토리 메서드 사용
    public static FxRate create(String baseCurrency, String quoteCurrency, BigDecimal midRate,
                                 String source, LocalDateTime fetchedAt) {
        return new FxRate(baseCurrency, quoteCurrency, midRate, source, fetchedAt);
    }
}
