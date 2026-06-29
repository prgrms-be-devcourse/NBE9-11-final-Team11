package com.fxflow.domain.fxrate.service;

import com.fxflow.domain.fxrate.dto.response.FxRateHistoryResponse;
import com.fxflow.domain.fxrate.entity.FxRate;
import com.fxflow.domain.fxrate.enums.FxRateHistoryPeriod;
import com.fxflow.domain.fxrate.repository.FxRateHistoryRow;
import com.fxflow.domain.fxrate.repository.FxRateRepository;
import com.fxflow.global.fx.ExchangeRateProvider;
import com.fxflow.global.fx.FxRateSnapshot;
import com.fxflow.global.util.KstClock;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * ExchangeRateProvider 포트의 fxrate 도메인 구현 (조회 전용).
 * 외부 API를 호출하지 않고, 스케줄러가 저장해 둔 최신 환율을 DB에서 읽어 제공한다.
 * (2단계에서 Redis 우선 조회 → DB fallback 으로 교체 예정)
 */
@Service
@RequiredArgsConstructor
@Primary
public class FxRateQueryService implements ExchangeRateProvider {

    private final FxRateRepository fxRateRepository;

    // 전일 대비 변동의 기준 시각 — 외환시장 마감 시각인 오후 3시 30분
    private static final LocalTime BASELINE_TIME = LocalTime.of(15, 30);

    @Override
    @Transactional(readOnly = true)
    public Optional<FxRateSnapshot> getLatestRate(String baseCurrency, String quoteCurrency) {
        return fxRateRepository
                .findFirstByBaseCurrencyAndQuoteCurrencyOrderByFetchedAtDesc(baseCurrency, quoteCurrency)
                .map(this::toSnapshot);
    }

    // 환율 이력 조회 — 기간별 윈도우/버킷 단위로 집계해 시계열 응답을 만든다.
    @Transactional(readOnly = true)
    public FxRateHistoryResponse getHistory(String baseCurrency, String quoteCurrency, FxRateHistoryPeriod period) {
        LocalDateTime from = KstClock.now().minus(period.getLookback());
        List<FxRateHistoryRow> rows =
                fxRateRepository.findHistory(baseCurrency, quoteCurrency, from, period.getBucketUnit());
        return FxRateHistoryResponse.of(baseCurrency, quoteCurrency, period, rows);
    }

    // 전일 15:30 기준 매매기준율(mid).
    // 15:30 시점에 유효했던 값(fetched_at ≤ 전일 15:30 중 최신)을 기준값으로 사용한다.
    // 기준값이 없으면(예: 누적 데이터 부족) Optional.empty → 변동 정보 미노출.
    @Transactional(readOnly = true)
    public Optional<BigDecimal> getPreviousDayBaselineMid(String baseCurrency, String quoteCurrency) {
        LocalDateTime target = LocalDate.now(KstClock.ZONE).minusDays(1).atTime(BASELINE_TIME);
        return fxRateRepository
                .findFirstByBaseCurrencyAndQuoteCurrencyAndFetchedAtLessThanEqualOrderByFetchedAtDesc(
                        baseCurrency, quoteCurrency, target)
                .map(FxRate::getMidRate);
    }

    private FxRateSnapshot toSnapshot(FxRate fxRate) {
        return new FxRateSnapshot(
                fxRate.getBaseCurrency(),
                fxRate.getQuoteCurrency(),
                fxRate.getMidRate(),
                fxRate.getSpread(),
                fxRate.getFetchedAt()
        );
    }
}
