package com.fxflow.domain.fxrate.repository;

import com.fxflow.domain.fxrate.entity.FxRate;
import com.fxflow.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 이력 버킷 집계(date_trunc + AVG)와 전일 기준값 as-of 조회는 PostgreSQL 전용 동작이므로
 * 실 DB(Testcontainers)로 검증한다.
 */
@DisplayName("FxRateRepository - 이력 버킷 집계 / 전일 기준값 (실 DB)")
class FxRateRepositoryHistoryTest extends AbstractIntegrationTest {

    @Autowired
    private FxRateRepository fxRateRepository;

    private void save(String mid, LocalDateTime fetchedAt) {
        fxRateRepository.save(FxRate.create("USD", "KRW", new BigDecimal(mid), "TwelveData", fetchedAt));
    }

    @BeforeEach
    void clean() {
        fxRateRepository.deleteAll();
    }

    @Test
    @DisplayName("시간(hour) 버킷으로 평균 집계하고 시간 오름차순으로 반환한다")
    void findHistory_hourBucketAverage() {
        // given - 09시에 2건(1300,1310), 10시에 1건(1320)
        save("1300", LocalDateTime.of(2026, 6, 18, 9, 10));
        save("1310", LocalDateTime.of(2026, 6, 18, 9, 50));
        save("1320", LocalDateTime.of(2026, 6, 18, 10, 5));

        // when
        List<FxRateHistoryRow> rows = fxRateRepository.findHistory(
                "USD", "KRW", LocalDateTime.of(2026, 6, 18, 0, 0), "hour");

        // then - 09시 버킷 평균 1305, 10시 버킷 1320
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).getBucket()).isEqualTo(LocalDateTime.of(2026, 6, 18, 9, 0));
        assertThat(rows.get(0).getRate()).isEqualByComparingTo("1305");
        assertThat(rows.get(1).getBucket()).isEqualTo(LocalDateTime.of(2026, 6, 18, 10, 0));
        assertThat(rows.get(1).getRate()).isEqualByComparingTo("1320");
    }

    @Test
    @DisplayName("일(day) 버킷으로 평균 집계한다 (1M 경로)")
    void findHistory_dayBucketAverage() {
        // given - 6/18에 2건(1300,1320), 6/19에 1건(1340)
        save("1300", LocalDateTime.of(2026, 6, 18, 9, 0));
        save("1320", LocalDateTime.of(2026, 6, 18, 21, 0));
        save("1340", LocalDateTime.of(2026, 6, 19, 9, 0));

        // when
        List<FxRateHistoryRow> rows = fxRateRepository.findHistory(
                "USD", "KRW", LocalDateTime.of(2026, 6, 1, 0, 0), "day");

        // then - 6/18 버킷 평균 1310, 6/19 버킷 1340 (버킷 시각은 00:00)
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).getBucket()).isEqualTo(LocalDateTime.of(2026, 6, 18, 0, 0));
        assertThat(rows.get(0).getRate()).isEqualByComparingTo("1310");
        assertThat(rows.get(1).getBucket()).isEqualTo(LocalDateTime.of(2026, 6, 19, 0, 0));
        assertThat(rows.get(1).getRate()).isEqualByComparingTo("1340");
    }

    @Test
    @DisplayName("from 이전 데이터는 집계에서 제외한다")
    void findHistory_excludesBeforeFrom() {
        save("1000", LocalDateTime.of(2026, 6, 17, 23, 0)); // from 이전 → 제외
        save("1300", LocalDateTime.of(2026, 6, 18, 9, 0));

        List<FxRateHistoryRow> rows = fxRateRepository.findHistory(
                "USD", "KRW", LocalDateTime.of(2026, 6, 18, 0, 0), "hour");

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getRate()).isEqualByComparingTo("1300");
    }

    @Test
    @DisplayName("전일 기준값 - 15:30 이전 중 가장 최근 레코드를 선택한다 (2분 수집 오차 처리)")
    void baseline_picksLatestAtOrBefore1530() {
        LocalDateTime target = LocalDateTime.of(2026, 6, 17, 15, 30);
        save("1290", LocalDateTime.of(2026, 6, 17, 15, 0));   // 후보
        save("1300", LocalDateTime.of(2026, 6, 17, 15, 28));  // 15:30 직전 최신 → 선택
        save("1310", LocalDateTime.of(2026, 6, 17, 15, 32));  // 15:30 이후 → 제외

        Optional<FxRate> result = fxRateRepository
                .findFirstByBaseCurrencyAndQuoteCurrencyAndFetchedAtLessThanEqualOrderByFetchedAtDesc(
                        "USD", "KRW", target);

        assertThat(result).isPresent();
        assertThat(result.get().getMidRate()).isEqualByComparingTo("1300");
    }

    @Test
    @DisplayName("기준 시각 이전 데이터가 없으면 기준값은 비어 있다")
    void baseline_emptyWhenNoData() {
        save("1310", LocalDateTime.of(2026, 6, 17, 15, 32)); // 기준 시각 이후만 존재

        Optional<FxRate> result = fxRateRepository
                .findFirstByBaseCurrencyAndQuoteCurrencyAndFetchedAtLessThanEqualOrderByFetchedAtDesc(
                        "USD", "KRW", LocalDateTime.of(2026, 6, 17, 15, 30));

        assertThat(result).isEmpty();
    }
}
