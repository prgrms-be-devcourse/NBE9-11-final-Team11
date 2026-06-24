package com.fxflow.domain.fxrate.repository;

import com.fxflow.domain.fxrate.entity.FxRate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FxRateRepository extends JpaRepository<FxRate, Long> {

    // 통화쌍의 가장 최신 환율 1건 조회 (append-only 테이블이므로 최신 수집분이 현재 환율)
    // @Query 사용 시 다른 코드를 참조하게 되고, "First" 가 SQL LIMIT 1 을 선언적으로 보장한다는 점에서 유지
    Optional<FxRate> findFirstByBaseCurrencyAndQuoteCurrencyOrderByFetchedAtDesc(String baseCurrency, String quoteCurrency);
}
