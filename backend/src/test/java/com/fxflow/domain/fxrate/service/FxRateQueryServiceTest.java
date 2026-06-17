package com.fxflow.domain.fxrate.service;

import com.fxflow.domain.fxrate.entity.FxRate;
import com.fxflow.domain.fxrate.repository.FxRateRepository;
import com.fxflow.global.fx.FxRateSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * 도메인 간 환율 제공 포트(ExchangeRateProvider) 구현 검증.
 * 외부 API를 호출하지 않고, 저장된 최신 환율을 스냅샷으로 매핑하는 조회 책임만 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FxRateQueryService - 최신 환율 스냅샷 조회 포트")
class FxRateQueryServiceTest {

    @Mock
    private FxRateRepository fxRateRepository;

    @InjectMocks
    private FxRateQueryService fxRateQueryService;

    @Test
    @DisplayName("최신 환율이 있으면 스냅샷으로 매핑하고 매수/매도 적용가를 파생한다")
    void getLatestRate_found() {
        // given - 저장된 최신 환율 (mid 1300, 기본 스프레드 0.01)
        LocalDateTime fetchedAt = LocalDateTime.of(2026, 6, 17, 9, 0, 0);
        FxRate fxRate = FxRate.create("USD", "KRW", new BigDecimal("1300"), "TwelveData", fetchedAt);
        given(fxRateRepository.findFirstByBaseCurrencyAndQuoteCurrencyOrderByFetchedAtDesc("USD", "KRW"))
                .willReturn(Optional.of(fxRate));

        // when
        Optional<FxRateSnapshot> result = fxRateQueryService.getLatestRate("USD", "KRW");

        // then
        assertThat(result).isPresent();
        FxRateSnapshot snapshot = result.get();
        assertThat(snapshot.baseCurrency()).isEqualTo("USD");
        assertThat(snapshot.quoteCurrency()).isEqualTo("KRW");
        assertThat(snapshot.midRate()).isEqualByComparingTo("1300");
        assertThat(snapshot.spread()).isEqualByComparingTo("0.01");
        assertThat(snapshot.fetchedAt()).isEqualTo(fetchedAt);
        // 파생 적용가: 매수 = 1300 × 1.01 = 1313, 매도 = 1300 × 0.99 = 1287
        assertThat(snapshot.buyRate()).isEqualByComparingTo("1313.00");
        assertThat(snapshot.sellRate()).isEqualByComparingTo("1287.00");
    }

    @Test
    @DisplayName("저장된 환율이 없으면 Optional.empty를 반환한다")
    void getLatestRate_notFound() {
        // given
        given(fxRateRepository.findFirstByBaseCurrencyAndQuoteCurrencyOrderByFetchedAtDesc("USD", "KRW"))
                .willReturn(Optional.empty());

        // when
        Optional<FxRateSnapshot> result = fxRateQueryService.getLatestRate("USD", "KRW");

        // then
        assertThat(result).isEmpty();
    }
}
