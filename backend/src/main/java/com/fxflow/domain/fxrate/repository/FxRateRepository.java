package com.fxflow.domain.fxrate.repository;

import com.fxflow.domain.fxrate.entity.FxRate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FxRateRepository extends JpaRepository<FxRate, Long> {

    // 통화쌍의 가장 최신 환율 1건 조회 (append-only 테이블이므로 최신 수집분이 현재 환율)
    Optional<FxRate> findFirstByBaseCurrencyAndQuoteCurrencyOrderByFetchedAtDesc(String baseCurrency, String quoteCurrency);

    // 통화쌍 환율 조회 — append-only 특성상 다건이면 예외 발생 가능
    Optional<FxRate> findByBaseCurrencyAndQuoteCurrency(
            String fromCurrency,
            String toCurrency
    );
}
