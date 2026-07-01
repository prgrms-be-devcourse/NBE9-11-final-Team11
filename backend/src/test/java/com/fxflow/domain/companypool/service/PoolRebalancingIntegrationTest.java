package com.fxflow.domain.companypool.service;

import com.fxflow.domain.companypool.entity.CompanyPool;
import com.fxflow.domain.companypool.entity.RebalancingOrder;
import com.fxflow.domain.companypool.enums.CappedBy;
import com.fxflow.domain.companypool.repository.CompanyPoolRepository;
import com.fxflow.domain.companypool.repository.RebalancingRepository;
import com.fxflow.domain.fxrate.service.FxRateQueryService;
import com.fxflow.global.fx.FxRateSnapshot;
import com.fxflow.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

// test container
/**
 * 풀 리밸런싱 체인 통합 테스트
 *
 * 고정 풀 설정:
 *   KRW: target=10B, safeFloor=8B(80%), floor=6B(60%), ceiling=12B
 *   USD: target=6.5M, safeFloor=5.2M(80%), floor=3.9M(60%), ceiling=7.8M
 *   mid rate: 1300 KRW/USD, applied: 1300 × 1.002 = 1302.6
 *
 * 리밸런싱 로직: floor(60%) 미만 시 발동, safeFloor(80%)까지 채움 (매도 풀도 safeFloor까지만 소진)
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
        given(exchangeRateProvider.getLatestRateOrThrowIfStale("USD", "KRW")).willReturn(
                Optional.of(new FxRateSnapshot(
                        "USD", "KRW", new BigDecimal("1300"), new BigDecimal("0.01"), LocalDateTime.now())));
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.execute("TRUNCATE TABLE company_pools CASCADE");
    }

    // ── 케이스 1 ─────────────────────────────────────────────────────────────
    // KRW 출금 → KRW floor(60%) 미만 / shortageToSafeFloor < USD safeFloor 여유분 → safeFloor까지 전액 복구
    //
    //   KRW=10B(정상), USD=8M(정상)
    //   출금 5B → KRW=5B(floor 미만), shortageToSafeFloor=3B
    //   USD surplusAboveSafeFloor=2.8M × 1302.6=3,647,280,000 > 3B → 매입=3B
    //   KRW after=8B(=safeFloor), USD after≈5.7M → 둘 다 정상, capping 없음
    @Test
    @DisplayName("KRW floor 미만: shortageToSafeFloor < USD 여유분 → safeFloor까지 전액 복구, 둘 다 정상")
    void krwBelowFloor_fullRecovery_whenShortageIsLessThanUsdSurplus() {
        insertPool("KRW", "10000000000", "10000000000", "6000000000", "8000000000", "12000000000");
        insertPool("USD",    "8000000",    "6500000",    "3900000",    "5200000",    "7800000");

        companyPoolService.withdraw("case1-withdraw", "KRW", new BigDecimal("5000000000"));

        CompanyPool krw = companyPoolRepository.findByCurrencyCode("KRW").orElseThrow();
        CompanyPool usd = companyPoolRepository.findByCurrencyCode("USD").orElseThrow();
        assertThat(krw.getBalance()).isGreaterThanOrEqualTo(krw.getFloorBalance());
        assertThat(usd.getBalance()).isGreaterThan(usd.getFloorBalance());
        assertThat(latestOrder().getCappedBy()).isNull();
    }

    // ── 케이스 2 ─────────────────────────────────────────────────────────────
    // KRW 출금 → KRW floor(60%) 미만 / shortageToSafeFloor > USD safeFloor 여유분 → USD safeFloor까지 capping
    //
    //   KRW=10B(정상), USD=6.5M(정상, surplusAboveSafeFloor=1.3M × 1302.6=1.693B)
    //   출금 5B → KRW=5B(floor 미만), shortage=3B > 1.693B
    //   매입=1.693B(USD safeFloor 소진), sellAmount=1.3M
    //   KRW after=6.693B(정상), USD after=5.2M(=safeFloor, 정상) → 둘 다 정상
    @Test
    @DisplayName("KRW floor 미만: shortageToSafeFloor > USD 여유분 → USD safeFloor까지 capping, 둘 다 정상")
    void krwBelowFloor_partialRecovery_cappedByUsdFloor_bothStillNormal() {
        insertPool("KRW", "10000000000", "10000000000", "6000000000", "8000000000", "12000000000");
        insertPool("USD",    "6500000",    "6500000",    "3900000",    "5200000",    "7800000");

        companyPoolService.withdraw("case2-withdraw", "KRW", new BigDecimal("5000000000"));

        CompanyPool krw = companyPoolRepository.findByCurrencyCode("KRW").orElseThrow();
        CompanyPool usd = companyPoolRepository.findByCurrencyCode("USD").orElseThrow();
        assertThat(krw.getBalance()).isGreaterThanOrEqualTo(krw.getFloorBalance());
        assertThat(usd.getBalance()).isGreaterThanOrEqualTo(usd.getFloorBalance());
        assertThat(latestOrder().getCappedBy()).isEqualTo(CappedBy.USD_FLOOR);
    }

    // ── 케이스 3 ─────────────────────────────────────────────────────────────
    // 해외송금(KRW↑ USD↓) → USD floor(60%) 미만 / shortageToSafeFloor < KRW safeFloor 여유분 → 전액 복구
    //
    //   KRW=10B(정상), USD=4.5M(정상)
    //   해외송금: 수취 KRW +1,302,600,000 (=1M × 1302.6), 지급 USD -1M
    //   → KRW=11.3026B(정상), USD=3.5M(floor 미만), shortage=1.7M
    //   KRW surplusAboveSafeFloor=3.3026B → in USD≈2.535M > 1.7M → 전액 복구
    //   USD after=5.2M(=safeFloor), KRW after≈9.09B → 둘 다 정상, capping 없음
    @Test
    @DisplayName("USD floor 미만: shortageToSafeFloor < KRW 여유분 → safeFloor까지 전액 복구, 둘 다 정상")
    void usdBelowFloor_fullRecovery_whenShortageIsLessThanKrwSurplus() {
        insertPool("KRW", "10000000000", "10000000000", "6000000000", "8000000000", "12000000000");
        insertPool("USD",    "4500000",    "6500000",    "3900000",    "5200000",    "7800000");

        companyPoolService.depositForRemittance("case3-recv", "KRW", new BigDecimal("1302600000"), "remittance-inbound-001");
        companyPoolService.withdrawForRemittance("case3-pay", "USD", new BigDecimal("1000000"), "remittance-outbound-001");

        CompanyPool krw = companyPoolRepository.findByCurrencyCode("KRW").orElseThrow();
        CompanyPool usd = companyPoolRepository.findByCurrencyCode("USD").orElseThrow();
        assertThat(usd.getBalance()).isGreaterThanOrEqualTo(usd.getFloorBalance());
        assertThat(krw.getBalance()).isGreaterThan(krw.getFloorBalance());
        assertThat(latestOrder().getCappedBy()).isNull();
    }

    // ── 케이스 4 ─────────────────────────────────────────────────────────────
    // 해외송금(KRW↑ USD↓) → USD floor(60%) 미만 / shortageToSafeFloor > KRW safeFloor 여유분 → KRW safeFloor까지 capping
    //
    //   KRW=8.5B(정상), USD=4.5M(정상)
    //   해외송금: 수취 KRW +1,302,600,000, 지급 USD -1M
    //   → KRW=9.8026B(정상), USD=3.5M(floor 미만), shortage=1.7M
    //   KRW surplusAboveSafeFloor=1.8026B → in USD≈1.384M < 1.7M → capping
    //   USD after≈4.884M(정상), KRW after≈8B(=safeFloor, 정상) → 둘 다 정상
    @Test
    @DisplayName("USD floor 미만: shortageToSafeFloor > KRW 여유분 → KRW safeFloor까지 capping, 둘 다 정상")
    void usdBelowFloor_partialRecovery_cappedByKrwFloor_bothStillNormal() {
        insertPool("KRW", "8500000000", "10000000000", "6000000000", "8000000000", "12000000000");
        insertPool("USD",  "4500000",    "6500000",    "3900000",    "5200000",    "7800000");

        companyPoolService.depositForRemittance("case4-recv", "KRW", new BigDecimal("1302600000"), "remittance-inbound-002");
        companyPoolService.withdrawForRemittance("case4-pay", "USD", new BigDecimal("1000000"), "remittance-outbound-002");

        CompanyPool krw = companyPoolRepository.findByCurrencyCode("KRW").orElseThrow();
        CompanyPool usd = companyPoolRepository.findByCurrencyCode("USD").orElseThrow();
        assertThat(usd.getBalance()).isGreaterThanOrEqualTo(usd.getFloorBalance());
        assertThat(krw.getBalance()).isGreaterThanOrEqualTo(krw.getFloorBalance());
        assertThat(latestOrder().getCappedBy()).isEqualTo(CappedBy.KRW_FLOOR);
    }

    // ── 케이스 5 ─────────────────────────────────────────────────────────────
    // KRW 급락 → USD safeFloor 여유분이 KRW shortageToSafeFloor에 미치지 못해 리밸런싱 후에도 floor 미달
    //
    //   KRW=10B(정상), USD=6.5M(정상, surplusAboveSafeFloor=1.3M → KRW 환산 1.693B)
    //   출금 9B → KRW=1B(floor 미만), shortage=7B > 1.693B
    //   매입=1.693B(USD safeFloor 소진) → KRW after=2.693B < floor(6B) 여전히 미달
    @Test
    @DisplayName("KRW 급락: 리밸런싱 후에도 floor(60%) 미달 (USD 여유분 부족으로 완전 복구 불가)")
    void krwBelowFloor_stillBelowFloorAfterRebalancing_whenUsdSurplusNotEnoughToReachFloor() {
        insertPool("KRW", "10000000000", "10000000000", "6000000000", "8000000000", "12000000000");
        insertPool("USD",    "6500000",    "6500000",    "3900000",    "5200000",    "7800000");

        companyPoolService.withdraw("case5-withdraw", "KRW", new BigDecimal("9000000000"));

        CompanyPool krw = companyPoolRepository.findByCurrencyCode("KRW").orElseThrow();
        CompanyPool usd = companyPoolRepository.findByCurrencyCode("USD").orElseThrow();
        assertThat(krw.getBalance()).isLessThan(krw.getFloorBalance());            // 여전히 floor 미달
        assertThat(usd.getBalance()).isGreaterThanOrEqualTo(usd.getFloorBalance()); // USD는 floor 이상 유지
        assertThat(latestOrder().getCappedBy()).isEqualTo(CappedBy.USD_FLOOR);
    }

    private RebalancingOrder latestOrder() {
        List<RebalancingOrder> orders = rebalancingRepository.findAllByOrderByCreatedAtDesc();
        assertThat(orders).isNotEmpty();
        return orders.getFirst();
    }

    private void insertPool(String currency, String balance, String target,
                             String floor, String safeFloor, String ceiling) {
        jdbcTemplate.update(
                "INSERT INTO company_pools (currency_code, balance, target_balance, floor_balance, safe_floor_balance, ceiling_balance, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW()) " +
                "ON CONFLICT (currency_code) DO UPDATE SET " +
                "balance = EXCLUDED.balance, target_balance = EXCLUDED.target_balance, " +
                "floor_balance = EXCLUDED.floor_balance, safe_floor_balance = EXCLUDED.safe_floor_balance, " +
                "ceiling_balance = EXCLUDED.ceiling_balance, updated_at = NOW()",
                currency, new BigDecimal(balance), new BigDecimal(target),
                new BigDecimal(floor), new BigDecimal(safeFloor), new BigDecimal(ceiling));
    }
}