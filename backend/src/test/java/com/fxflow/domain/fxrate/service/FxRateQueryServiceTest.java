package com.fxflow.domain.fxrate.service;

import com.fxflow.domain.fxrate.dto.response.FxRateHistoryResponse;
import com.fxflow.domain.fxrate.entity.FxRate;
import com.fxflow.domain.fxrate.enums.FxRateHistoryPeriod;
import com.fxflow.domain.fxrate.repository.FxRateHistoryRow;
import com.fxflow.domain.fxrate.repository.FxRateRepository;
import com.fxflow.domain.fxrate.exception.FxRateErrorCode;
import com.fxflow.global.exception.BusinessException;
import com.fxflow.global.fx.FxRateSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;

/**
 * 도메인 간 환율 제공 포트(ExchangeRateProvider) 구현 검증.
 * 외부 API를 호출하지 않고, 저장된 최신 환율을 스냅샷으로 매핑하는 조회 책임만 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FxRateQueryService - 최신 환율 스냅샷 조회 포트")
class FxRateQueryServiceTest {

    @Mock
    private FxRateRepository fxRateRepository;

    @Mock
    private FxRateService fxRateService;

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
        assertThat(snapshot.spread()).isEqualByComparingTo("0");
        assertThat(snapshot.fetchedAt()).isEqualTo(fetchedAt);
        assertThat(snapshot.buyRate()).isEqualByComparingTo("1300.00");
        assertThat(snapshot.sellRate()).isEqualByComparingTo("1300.00");
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

    @Test
    @DisplayName("신선도 검증을 통과하면 최신 환율을 그대로 반환한다 (거래용 조회)")
    void getLatestRateOrThrowIfStale_freshReturnsRate() {
        // given - fxRateService.validateFreshness()는 기본적으로 아무 예외도 던지지 않음(정상 상태)
        LocalDateTime fetchedAt = LocalDateTime.of(2026, 6, 17, 9, 0, 0);
        FxRate fxRate = FxRate.create("USD", "KRW", new BigDecimal("1300"), "TwelveData", fetchedAt);
        given(fxRateRepository.findFirstByBaseCurrencyAndQuoteCurrencyOrderByFetchedAtDesc("USD", "KRW"))
                .willReturn(Optional.of(fxRate));

        // when
        Optional<FxRateSnapshot> result = fxRateQueryService.getLatestRateOrThrowIfStale("USD", "KRW");

        // then
        assertThat(result).isPresent();
        assertThat(result.get().midRate()).isEqualByComparingTo("1300");
    }

    @Test
    @DisplayName("신선도 검증에서 예외가 발생하면 그대로 전파해 거래를 차단한다")
    void getLatestRateOrThrowIfStale_staleThrows() {
        // given - 마지막 API 성공 호출이 임계값을 초과한 상태를 가정
        willThrow(new BusinessException(FxRateErrorCode.FX_RATE_STALE))
                .given(fxRateService).validateFreshness();

        // when & then
        assertThatThrownBy(() -> fxRateQueryService.getLatestRateOrThrowIfStale("USD", "KRW"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(FxRateErrorCode.FX_RATE_STALE);
    }

    @Test
    @DisplayName("전일 기준값 조회 시 '전일 15:30' 이전 최신 환율을 as-of로 조회한다")
    void getPreviousDayBaselineMid_usesYesterday1530AsOf() {
        // given
        FxRate baseline = FxRate.create("USD", "KRW", new BigDecimal("1300"), "TwelveData",
                LocalDateTime.of(2026, 6, 17, 15, 28));
        given(fxRateRepository.findFirstByBaseCurrencyAndQuoteCurrencyAndFetchedAtLessThanEqualOrderByFetchedAtDesc(
                eq("USD"), eq("KRW"), any(LocalDateTime.class)))
                .willReturn(Optional.of(baseline));

        // when
        Optional<BigDecimal> result = fxRateQueryService.getPreviousDayBaselineMid("USD", "KRW");

        // then - 반환값은 기준 환율(mid)
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualByComparingTo("1300");

        // and - 조회 기준 시각은 전일 15:30
        ArgumentCaptor<LocalDateTime> targetCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        org.mockito.Mockito.verify(fxRateRepository)
                .findFirstByBaseCurrencyAndQuoteCurrencyAndFetchedAtLessThanEqualOrderByFetchedAtDesc(
                        eq("USD"), eq("KRW"), targetCaptor.capture());
        assertThat(targetCaptor.getValue()).isEqualTo(LocalDate.now().minusDays(1).atTime(15, 30));
    }

    @Test
    @DisplayName("기준값이 없으면 전일 대비 변동 기준은 Optional.empty 를 반환한다")
    void getPreviousDayBaselineMid_empty() {
        given(fxRateRepository.findFirstByBaseCurrencyAndQuoteCurrencyAndFetchedAtLessThanEqualOrderByFetchedAtDesc(
                eq("USD"), eq("KRW"), any(LocalDateTime.class)))
                .willReturn(Optional.empty());

        assertThat(fxRateQueryService.getPreviousDayBaselineMid("USD", "KRW")).isEmpty();
    }

    @Test
    @DisplayName("이력 조회는 버킷 행을 시계열 포인트로 매핑하고 mid 자리수(8)로 정규화한다")
    void getHistory_mapsRowsToPoints() {
        // given - 1주일은 시간 버킷으로 집계 (AVG 결과의 잔여 소수 정규화 확인)
        FxRateHistoryRow row = mock(FxRateHistoryRow.class);
        given(row.getBucket()).willReturn(LocalDateTime.of(2026, 6, 18, 9, 0));
        given(row.getRate()).willReturn(new BigDecimal("1305.123456785"));
        given(fxRateRepository.findHistory(eq("USD"), eq("KRW"), any(LocalDateTime.class), eq("hour")))
                .willReturn(List.of(row));

        // when
        FxRateHistoryResponse response = fxRateQueryService.getHistory("USD", "KRW", FxRateHistoryPeriod.ONE_WEEK);

        // then
        assertThat(response.period()).isEqualTo("1W");
        assertThat(response.points()).hasSize(1);
        assertThat(response.points().get(0).timestamp()).isEqualTo(LocalDateTime.of(2026, 6, 18, 9, 0));
        // 8자리 HALF_UP 정규화: 1305.123456785 → 1305.12345679 (scale 8)
        assertThat(response.points().get(0).midRate()).isEqualByComparingTo("1305.12345679");
    }
}
