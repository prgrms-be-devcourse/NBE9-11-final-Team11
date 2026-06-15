package com.fxflow.domain.fxrate.entity;

import com.fxflow.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
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
    private String baseCurrency;

    @Column(name = "quote_currency", length = 10, nullable = false)
    private String quoteCurrency;

    @Column(name = "mid_rate", precision = 18, scale = 2, nullable = false)
    private BigDecimal midRate;

    @Column(name = "spread", precision = 18, scale = 2, nullable = false)
    private BigDecimal spread;

    @Column(name = "applied_rate", precision = 18, scale = 2, nullable = false)
    private BigDecimal appliedRate;

    @Column(name = "source", length = 50, nullable = false)
    private String source;

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;

    @Builder
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
}
