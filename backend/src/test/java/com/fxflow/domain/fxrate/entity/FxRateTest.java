package com.fxflow.domain.fxrate.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FxRate 엔티티 정적 팩토리(create) 검증.
 * 스프레드는 외부 입력이 아니라 엔티티가 도메인 정책(DEFAULT_SPREAD)으로 강제한다.
 */
@DisplayName("FxRate 엔티티 - 정적 팩토리 create()")
class FxRateTest {

    @Test
    @DisplayName("create()는 전달값을 채우고, 스프레드는 기본 정책값(0)으로 설정한다")
    void create_setsFieldsAndDefaultSpread() {
        // given
        LocalDateTime fetchedAt = LocalDateTime.of(2026, 6, 17, 9, 0, 0);

        // when - 호출부는 spread를 넘기지 않는다 (엔티티가 정책값을 강제)
        FxRate fxRate = FxRate.create("USD", "KRW", new BigDecimal("1386.50"), "TwelveData", fetchedAt);

        // then
        assertThat(fxRate.getBaseCurrency()).isEqualTo("USD");
        assertThat(fxRate.getQuoteCurrency()).isEqualTo("KRW");
        assertThat(fxRate.getMidRate()).isEqualByComparingTo("1386.50");
        assertThat(fxRate.getSource()).isEqualTo("TwelveData");
        assertThat(fxRate.getFetchedAt()).isEqualTo(fetchedAt);
        // 정책 고정값: 기획 확정 전 임시 1%
        assertThat(fxRate.getSpread()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("midRate가 0 이하이면 IllegalArgumentException을 던진다")
    void create_rejectsNonPositiveMidRate() {
        LocalDateTime fetchedAt = LocalDateTime.of(2026, 6, 17, 9, 0, 0);

        assertThatThrownBy(() ->
                FxRate.create("USD", "KRW", BigDecimal.ZERO, "TwelveData", fetchedAt))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() ->
                FxRate.create("USD", "KRW", new BigDecimal("-1"), "TwelveData", fetchedAt))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("midRate가 null이면 IllegalArgumentException을 던진다")
    void create_rejectsNullMidRate() {
        LocalDateTime fetchedAt = LocalDateTime.of(2026, 6, 17, 9, 0, 0);

        assertThatThrownBy(() ->
                FxRate.create("USD", "KRW", null, "TwelveData", fetchedAt))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("통화코드가 null이거나 공백이면 IllegalArgumentException을 던진다")
    void create_rejectsBlankCurrency() {
        LocalDateTime fetchedAt = LocalDateTime.of(2026, 6, 17, 9, 0, 0);
        BigDecimal midRate = new BigDecimal("1386.50");

        assertThatThrownBy(() ->
                FxRate.create(null, "KRW", midRate, "TwelveData", fetchedAt))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() ->
                FxRate.create("USD", " ", midRate, "TwelveData", fetchedAt))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("source가 공백이거나 fetchedAt이 null이면 IllegalArgumentException을 던진다")
    void create_rejectsBlankSourceOrNullFetchedAt() {
        LocalDateTime fetchedAt = LocalDateTime.of(2026, 6, 17, 9, 0, 0);
        BigDecimal midRate = new BigDecimal("1386.50");

        assertThatThrownBy(() ->
                FxRate.create("USD", "KRW", midRate, "  ", fetchedAt))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() ->
                FxRate.create("USD", "KRW", midRate, "TwelveData", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
