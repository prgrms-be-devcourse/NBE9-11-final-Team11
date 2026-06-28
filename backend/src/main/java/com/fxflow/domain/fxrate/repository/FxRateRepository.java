package com.fxflow.domain.fxrate.repository;

import com.fxflow.domain.fxrate.entity.FxRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface FxRateRepository extends JpaRepository<FxRate, Long> {

    // 통화쌍의 가장 최신 환율 1건 조회 (append-only 테이블이므로 최신 수집분이 현재 환율)
    // @Query 사용 시 다른 코드를 참조하게 되고, "First" 가 SQL LIMIT 1 을 선언적으로 보장한다는 점에서 유지
    Optional<FxRate> findFirstByBaseCurrencyAndQuoteCurrencyOrderByFetchedAtDesc(String baseCurrency, String quoteCurrency);

    // 전일 대비 기준값 조회 — 지정 시각(target, 전일 15:30) 이전 중 가장 최근 1건.
    // 2분 주기 수집이라 정확히 15:30 레코드가 없을 수 있어, "15:30 시점에 유효했던 값"을 as-of로 선택한다.
    Optional<FxRate> findFirstByBaseCurrencyAndQuoteCurrencyAndFetchedAtLessThanEqualOrderByFetchedAtDesc(
            String baseCurrency, String quoteCurrency, LocalDateTime fetchedAt);

    // 이력 조회 — from 이후 데이터를 date_trunc 버킷 단위로 평균 집계해 시간 오름차순으로 반환.
    // unit 은 PostgreSQL date_trunc 단위(minute/hour/day). 파라미터 타입 추론 실패를 막기 위해 text 캐스팅.
    @Query(value = """
            SELECT date_trunc(CAST(:unit AS text), fetched_at) AS bucket, AVG(mid_rate) AS rate
            FROM fx_rates
            WHERE base_currency = :baseCurrency
              AND quote_currency = :quoteCurrency
              AND fetched_at >= :from
            GROUP BY bucket
            ORDER BY bucket ASC
            """, nativeQuery = true)
    List<FxRateHistoryRow> findHistory(
            @Param("baseCurrency") String baseCurrency,
            @Param("quoteCurrency") String quoteCurrency,
            @Param("from") LocalDateTime from,
            @Param("unit") String unit);
}
