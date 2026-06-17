package com.fxflow.domain.fxrate.service;

import com.fxflow.domain.fxrate.entity.FxRate;
import com.fxflow.domain.fxrate.repository.FxRateRepository;
import com.fxflow.global.fx.ExchangeRateProvider;
import com.fxflow.global.fx.FxRateSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Override
    @Transactional(readOnly = true)
    public Optional<FxRateSnapshot> getLatestRate(String baseCurrency, String quoteCurrency) {
        return fxRateRepository
                .findFirstByBaseCurrencyAndQuoteCurrencyOrderByFetchedAtDesc(baseCurrency, quoteCurrency)
                .map(this::toSnapshot);
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
