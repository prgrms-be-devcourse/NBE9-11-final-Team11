package com.fxflow.domain.companypool.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.fxflow.domain.companypool.entity.CompanyPool;
import com.fxflow.domain.companypool.entity.RebalancingOrder;
import com.fxflow.domain.companypool.enums.CappedBy;
import com.fxflow.domain.companypool.repository.CompanyPoolRepository;
import com.fxflow.domain.companypool.repository.RebalancingRepository;
import com.fxflow.domain.fxrate.service.FxRateQueryService;
import com.fxflow.global.fx.FxRateSnapshot;
import com.fxflow.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 풀 리밸런싱 체인 통합 테스트
 *
 * 고정 풀 설정:
 *   KRW: target=10B, floor=8B, ceiling=12B
 *   USD: target=6.5M, floor=5.2M, ceiling=7.8M
 *   mid rate: 1300 KRW/USD, applied: 1300 × 1.003 = 1303.9
 *
 * 4케이스 모두 두 풀이 정상 범위에서 출발하고, 리밸런싱 후 둘 다 정상 복귀.
 */
@DisplayName("풀 리밸런싱 체인 통합 테스트")
class PoolRebalancingIntegrationTest extends AbstractIntegrationTest {

    @Autowired private CompanyPoolService companyPoolService;
    @Autowired private CompanyPoolRepository companyPoolRepository;
    @Autowired private RebalancingRepository rebalancingRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockitoBean private FxRateQueryService exchangeRateProvider;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE company_pools CASCADE");
        given(exchangeRateProvider.getLatestRate("USD", "KRW")).willReturn(
                Optional.of(new FxRateSnapshot(
                        "USD", "KRW", new BigDecimal("1300"), new BigDecimal("0.01"), LocalDateTime.now())));
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.execute("TRUNCATE TABLE company_pools CASCADE");
    }

    // ── 케이스 1 ─────────────────────────────────────────────────────────────
    // 원화 출금 → KRW floor 미만 / 부족분 < USD 여유분(환산) → target까지 전액 복구
    //
    //   KRW=10B(정상), USD=7.5M(정상)
    //   출금 2.5B → KRW=7.5B(floor 미만), shortage=2.5B
    //   USD surplus=2.3M × 1303.9=2.998B  >  2.5B  → 매입=2.5B
    //   KRW after=10B, USD after=5.58M → 둘 다 정상, capping 없음
    @Test
    @DisplayName("KRW floor 미만: 부족분 < USD 여유분 → target까지 전액 복구, 둘 다 정상")
    void krwBelowFloor_fullRecovery_whenShortageIsLessThanUsdSurplus() {
        insertPool("KRW", "10000000000", "10000000000", "8000000000", "12000000000");
        insertPool("USD",    "7500000",    "6500000",    "5200000",    "7800000");

        companyPoolService.withdraw("case1-withdraw", "KRW", new BigDecimal("2500000000"));

        CompanyPool krw = companyPoolRepository.findByCurrencyCode("KRW").orElseThrow();
        CompanyPool usd = companyPoolRepository.findByCurrencyCode("USD").orElseThrow();
        assertThat(krw.getBalance()).isGreaterThanOrEqualTo(krw.getFloorBalance());
        assertThat(usd.getBalance()).isGreaterThan(usd.getFloorBalance()); // surplus 일부만 소진
        assertThat(latestOrder().getCappedBy()).isNull();
    }

    // ── 케이스 2 ─────────────────────────────────────────────────────────────
    // 원화 출금 → KRW floor 미만 / 부족분 > USD 여유분(환산) → USD floor까지 capping
    // USD 여유분이 KRW shortage-to-floor 이상이므로 KRW는 floor 이상으로 회복
    //
    //   KRW=10B(정상), USD=6.5M(정상, surplus=1.3M × 1303.9=1.695B)
    //   출금 3B → KRW=7B(floor 미만), shortage=3B  >  1.695B
    //   매입=1.695B(USD 전량 소진), sellAmount=1.3M (딱 맞아떨어짐)
    //   KRW after=8.695B(정상), USD after=5.2M(floor=정상) → 둘 다 정상
    @Test
    @DisplayName("KRW floor 미만: 부족분 > USD 여유분 → USD floor까지 capping, 둘 다 정상")
    void krwBelowFloor_partialRecovery_cappedByUsdFloor_bothStillNormal() {
        insertPool("KRW", "10000000000", "10000000000", "8000000000", "12000000000");
        insertPool("USD",    "6500000",    "6500000",    "5200000",    "7800000");

        companyPoolService.withdraw("case2-withdraw", "KRW", new BigDecimal("3000000000"));

        CompanyPool krw = companyPoolRepository.findByCurrencyCode("KRW").orElseThrow();
        CompanyPool usd = companyPoolRepository.findByCurrencyCode("USD").orElseThrow();
        assertThat(krw.getBalance()).isGreaterThanOrEqualTo(krw.getFloorBalance());
        assertThat(usd.getBalance()).isGreaterThanOrEqualTo(usd.getFloorBalance());
        assertThat(latestOrder().getCappedBy()).isEqualTo(CappedBy.USD_FLOOR);
    }

    // ── 케이스 3 ─────────────────────────────────────────────────────────────
    // 해외송금(KRW↑ USD↓) → USD floor 미만 / 부족분 < KRW 여유분(환산) → target까지 전액 복구
    //
    //   KRW=10B(정상), USD=5.5M(정상)
    //   해외송금: 수취 KRW +651.95M, 지급 USD -500K (applied rate 1303.9 기준)
    //   → KRW=10.652B(정상), USD=5M(floor 미만)
    //   shortage=1.5M, KRW surplus=2.652B → in USD=2.034M  >  1.5M → 전액 복구
    //   USD after=6.5M(target), KRW after=8.696B → 둘 다 정상, capping 없음
    @Test
    @DisplayName("USD floor 미만: 부족분 < KRW 여유분 → target까지 전액 복구, 둘 다 정상")
    void usdBelowFloor_fullRecovery_whenShortageIsLessThanKrwSurplus() {
        insertPool("KRW", "10000000000", "10000000000", "8000000000", "12000000000");
        insertPool("USD",    "5500000",    "6500000",    "5200000",    "7800000");

        // 해외송금: 수취 KRW = 500K USD × 1303.9 = 651,950,000
        companyPoolService.depositForRemittance("case3-recv", "KRW", new BigDecimal("651950000"), "case3-recv-ref");
        companyPoolService.withdrawForRemittance("case3-pay", "USD", new BigDecimal("500000"), "case3-pay-ref");

        CompanyPool krw = companyPoolRepository.findByCurrencyCode("KRW").orElseThrow();
        CompanyPool usd = companyPoolRepository.findByCurrencyCode("USD").orElseThrow();
        assertThat(usd.getBalance()).isGreaterThanOrEqualTo(usd.getFloorBalance());
        assertThat(krw.getBalance()).isGreaterThan(krw.getFloorBalance()); // surplus 일부만 소진
        assertThat(latestOrder().getCappedBy()).isNull();
    }

    // ── 케이스 4 ─────────────────────────────────────────────────────────────
    // 해외송금(KRW↑ USD↓) → USD floor 미만 / 부족분 > KRW 여유분(환산) → KRW floor까지 capping
    // KRW 여유분이 USD shortage-to-floor 이상이므로 USD는 floor 이상으로 회복
    //
    //   KRW=9B(정상), USD=5.5M(정상)
    //   해외송금: 수취 KRW +1,955.85M, 지급 USD -1.5M (applied rate 1303.9 기준)
    //   → KRW=10.956B(정상), USD=4M(floor 미만)
    //   shortage=2.5M, KRW surplus=2.956B → in USD=2.267M  <  2.5M → capping
    //   USD after=6.267M(정상), KRW after≈floor(정상) → 둘 다 정상
    @Test
    @DisplayName("USD floor 미만: 부족분 > KRW 여유분 → KRW floor까지 capping, 둘 다 정상")
    void usdBelowFloor_partialRecovery_cappedByKrwFloor_bothStillNormal() {
        insertPool("KRW", "9000000000", "10000000000", "8000000000", "12000000000");
        insertPool("USD",  "5500000",    "6500000",    "5200000",    "7800000");

        // 해외송금: 수취 KRW = 1.5M USD × 1303.9 = 1,955,850,000
        companyPoolService.depositForRemittance("case4-recv", "KRW", new BigDecimal("1955850000"), "case4-recv-ref");
        companyPoolService.withdrawForRemittance("case4-pay", "USD", new BigDecimal("1500000"), "case4-pay-ref");

        CompanyPool krw = companyPoolRepository.findByCurrencyCode("KRW").orElseThrow();
        CompanyPool usd = companyPoolRepository.findByCurrencyCode("USD").orElseThrow();
        assertThat(usd.getBalance()).isGreaterThanOrEqualTo(usd.getFloorBalance());
        assertThat(krw.getBalance()).isGreaterThanOrEqualTo(krw.getFloorBalance()); // KRW ≈ floor
        assertThat(latestOrder().getCappedBy()).isEqualTo(CappedBy.KRW_FLOOR);
    }

    // ── 케이스 5 ─────────────────────────────────────────────────────────────
    // KRW 급락 → USD 여유분이 KRW shortage-to-floor에도 미치지 못해 리밸런싱 후에도 floor 미달
    // (severe capping — 관리자 알림 발생, 단위 테스트에서 별도 검증)
    //
    //   KRW=10B(정상), USD=6.5M(정상, surplus=1.3M → KRW 환산 1.695B)
    //   출금 5B → KRW=5B(floor 미만), shortage-to-floor=3B  >  1.695B
    //   매입=1.695B(USD 전량 소진) → KRW after=6.695B  <  floor(8B) 여전히 미달
    @Test
    @DisplayName("KRW 급락: 리밸런싱 후에도 floor 미달 (USD 여유분 부족으로 완전 복구 불가)")
    void krwBelowFloor_stillBelowFloorAfterRebalancing_whenUsdSurplusNotEnoughToReachFloor() {
        insertPool("KRW", "10000000000", "10000000000", "8000000000", "12000000000");
        insertPool("USD",    "6500000",    "6500000",    "5200000",    "7800000");

        companyPoolService.withdraw("case5-withdraw", "KRW", new BigDecimal("5000000000"));

        CompanyPool krw = companyPoolRepository.findByCurrencyCode("KRW").orElseThrow();
        CompanyPool usd = companyPoolRepository.findByCurrencyCode("USD").orElseThrow();
        assertThat(krw.getBalance()).isLessThan(krw.getFloorBalance());       // 여전히 floor 미달
        assertThat(usd.getBalance()).isGreaterThanOrEqualTo(usd.getFloorBalance()); // USD는 floor 이상 유지
        assertThat(latestOrder().getCappedBy()).isEqualTo(CappedBy.USD_FLOOR);
    }

    private RebalancingOrder latestOrder() {
        List<RebalancingOrder> orders = rebalancingRepository.findAllByOrderByCreatedAtDesc();
        assertThat(orders).isNotEmpty();
        return orders.getFirst();
    }

    private void insertPool(String currency, String balance, String target, String floor, String ceiling) {
        jdbcTemplate.update(
                "INSERT INTO company_pools (currency_code, balance, target_balance, floor_balance, ceiling_balance, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, NOW(), NOW()) " +
                "ON CONFLICT (currency_code) DO UPDATE SET " +
                "balance = EXCLUDED.balance, target_balance = EXCLUDED.target_balance, " +
                "floor_balance = EXCLUDED.floor_balance, ceiling_balance = EXCLUDED.ceiling_balance, " +
                "updated_at = NOW()",
                currency, new BigDecimal(balance), new BigDecimal(target),
                new BigDecimal(floor), new BigDecimal(ceiling));
    }
}
