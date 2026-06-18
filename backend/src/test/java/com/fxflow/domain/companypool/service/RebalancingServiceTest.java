package com.fxflow.domain.companypool.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fxflow.domain.companypool.dto.response.RebalancingExecuteRes;
import com.fxflow.domain.companypool.entity.CompanyPool;
import com.fxflow.domain.companypool.entity.RebalancingOrder;
import com.fxflow.domain.companypool.enums.TriggerType;
import com.fxflow.domain.companypool.errorcode.PoolErrorCode;
import com.fxflow.domain.companypool.repository.CompanyPoolRepository;
import com.fxflow.domain.companypool.repository.RebalancingRepository;
import com.fxflow.global.exception.BusinessException;
import com.fxflow.global.fx.ExchangeRateProvider;
import com.fxflow.global.fx.FxRateSnapshot;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RebalancingServiceTest {

    @Mock private CompanyPoolRepository companyPoolRepository;
    @Mock private RebalancingRepository rebalancingRepository;
    @Mock private RebalancingAuditService auditService;
    @Mock private ExchangeRateProvider exchangeRateProvider;

    @InjectMocks private RebalancingService rebalancingService;

    private static final BigDecimal KRW_TARGET  = new BigDecimal("10000000000"); // 10B
    private static final BigDecimal KRW_FLOOR   = new BigDecimal("8000000000");  //  8B
    private static final BigDecimal KRW_CEILING = new BigDecimal("12000000000"); // 12B

    private static final BigDecimal USD_TARGET  = new BigDecimal("6500000"); // 650만
    private static final BigDecimal USD_FLOOR   = new BigDecimal("5200000"); // 520만
    private static final BigDecimal USD_CEILING = new BigDecimal("7800000"); // 780만

    private static final BigDecimal MID_RATE = new BigDecimal("1300"); // 1USD = 1300KRW

    @BeforeEach
    void setUp() {
        FxRateSnapshot snapshot = new FxRateSnapshot("USD", "KRW", MID_RATE, new BigDecimal("0.01"), java.time.LocalDateTime.now());
        given(exchangeRateProvider.getLatestRate("USD", "KRW")).willReturn(Optional.of(snapshot));
        given(companyPoolRepository.decreaseBalance(anyString(), any())).willReturn(1);
    }

    // 케이스 판별 ────────────────────────────────────────────────

    @Test
    @DisplayName("둘 다 정상 범위 → 리밸런싱 미실행")
    void execute_bothNormal_notExecuted() {
        givenPools(new BigDecimal("10000000000"), new BigDecimal("6500000")); // 10B, 6.5M

        RebalancingExecuteRes result = rebalancingService.execute(TriggerType.MANUAL, null);

        assertThat(result.executed()).isFalse();
        verify(rebalancingRepository, never()).save(any());
    }

    @Test
    @DisplayName("둘 다 floor 미만 → BOTH_BELOW_FLOOR 예외, MANUAL_REQUIRED 기록 저장 (별도 트랜잭션)")
    void execute_bothBelowFloor_throwsException() {
        givenPools(new BigDecimal("7000000000"), new BigDecimal("5000000")); // 7B, 500만

        assertThatThrownBy(() -> rebalancingService.execute(TriggerType.MANUAL, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(PoolErrorCode.BOTH_BELOW_FLOOR));
        verify(auditService).saveManualRequired(eq(TriggerType.MANUAL), any(), anyString());
        verify(rebalancingRepository, never()).save(any());
    }

    @Test
    @DisplayName("KRW ceiling 초과, USD 정상 → floor 미만 없으므로 미실행")
    void execute_krwAboveCeiling_usdNormal_notExecuted() {
        givenPools(new BigDecimal("13000000000"), new BigDecimal("6500000")); // 13B, 6.5M

        RebalancingExecuteRes result = rebalancingService.execute(TriggerType.MANUAL, null);

        assertThat(result.executed()).isFalse();
        verify(rebalancingRepository, never()).save(any());
    }

    // 매입/매도량 계산 ────────────────────────────────────────────
    @Test
    @DisplayName("KRW floor 미만 — 완전 체결 (부족분 < USD 여유분, cappedBy = null)")
    void execute_krwBelowFloor_fullFill() {
        // KRW: 7.9B → shortageToTarget = 10B - 7.9B = 2.1B
        // USD: 7M  → surplusAboveFloor = 7M - 5.2M = 1.8M
        // appliedRate = 1300 × 1.003 = 1303.9
        // maxBuyableKRW = 1,800,000 × 1303.9 = 2,347,020,000
        // 2.1B < 2.347B → 완전 체결, buyAmount = 2,100,000,000
        givenPools(new BigDecimal("7900000000"), new BigDecimal("7000000")); // KRW 7.9B, USD 7M

        RebalancingExecuteRes result = rebalancingService.execute(TriggerType.MANUAL, null);

        assertThat(result.executed()).isTrue();
        assertThat(result.buyCurrency()).isEqualTo("KRW");
        assertThat(result.sellCurrency()).isEqualTo("USD");
        assertThat(result.buyAmount()).isEqualByComparingTo("2100000000");
        assertThat(result.cappedBy()).isNull();
        verify(companyPoolRepository).increaseBalance(eq("KRW"), any());
        verify(companyPoolRepository).decreaseBalance(eq("USD"), any());
        verify(rebalancingRepository).save(any(RebalancingOrder.class));
    }

    @Test
    @DisplayName("KRW floor 미만 — 부분 체결 (부족분 > USD 여유분, cappedBy = USD_FLOOR)")
    void execute_krwBelowFloor_partialFill_cappedByUsdFloor() {
        // KRW: 7B → shortageToTarget = 3B
        // USD: 6.5M → surplusAboveFloor = 1.3M
        // maxBuyableKRW = 1,300,000 × 1303.9 = 1,695,070,000
        // 3B > 1.695B → 부분 체결, cappedBy = USD_FLOOR
        givenPools(new BigDecimal("7000000000"), new BigDecimal("6500000"));

        RebalancingExecuteRes result = rebalancingService.execute(TriggerType.MANUAL, null);

        assertThat(result.executed()).isTrue();
        assertThat(result.buyCurrency()).isEqualTo("KRW");
        assertThat(result.cappedBy()).isEqualTo("USD_FLOOR");
        assertThat(result.buyAmount()).isEqualByComparingTo("1695070000.00");
        verify(rebalancingRepository).save(any(RebalancingOrder.class));
    }

    @Test
    @DisplayName("USD floor 미만, KRW 정상 → USD 매입, KRW 매도")
    void execute_usdBelowFloor_buyUsd() {
        // USD: 500만 < 520만 floor
        // KRW: 10B 정상
        givenPools(new BigDecimal("10000000000"), new BigDecimal("5000000"));

        RebalancingExecuteRes result = rebalancingService.execute(TriggerType.MANUAL, null);

        assertThat(result.executed()).isTrue();
        assertThat(result.buyCurrency()).isEqualTo("USD");
        assertThat(result.sellCurrency()).isEqualTo("KRW");
        verify(companyPoolRepository).increaseBalance(eq("USD"), any());
        verify(companyPoolRepository).decreaseBalance(eq("KRW"), any());
    }

    @Test
    @DisplayName("KRW floor 미만 + USD ceiling 초과 → floor 우선, KRW 매입 (USD 여유 더 큼)")
    void execute_krwBelowFloor_usdAboveCeiling_floorPriority() {
        // KRW: 7B < 8B floor
        // USD: 800만 > 780만 ceiling
        givenPools(new BigDecimal("7000000000"), new BigDecimal("8000000"));

        RebalancingExecuteRes result = rebalancingService.execute(TriggerType.MANUAL, null);
        
        assertThat(result.buyCurrency()).isEqualTo("KRW");
        assertThat(result.sellCurrency()).isEqualTo("USD");
    }
    

    @Test
    @DisplayName("수동 실행 중 환율 데이터 없음(Optional.empty) → RATE_UNAVAILABLE 예외")
    void execute_manual_rateFetchFails_throwsRateUnavailable() {
        givenPools(new BigDecimal("7000000000"), new BigDecimal("6500000")); // KRW floor 미만
        given(exchangeRateProvider.getLatestRate("USD", "KRW")).willReturn(Optional.empty());

        assertThatThrownBy(() -> rebalancingService.execute(TriggerType.MANUAL, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(PoolErrorCode.RATE_UNAVAILABLE));
    }

    // 헬퍼 ───────────────────────────────────────────────────────

    private void givenPools(BigDecimal krwBalance, BigDecimal usdBalance) {
        CompanyPool krwPool = mockPool("KRW", krwBalance, KRW_TARGET, KRW_FLOOR, KRW_CEILING);
        CompanyPool usdPool = mockPool("USD", usdBalance, USD_TARGET, USD_FLOOR, USD_CEILING);
        given(companyPoolRepository.findByCurrencyCodeWithLock("KRW")).willReturn(Optional.of(krwPool));
        given(companyPoolRepository.findByCurrencyCodeWithLock("USD")).willReturn(Optional.of(usdPool));
    }

    private CompanyPool mockPool(String currencyCode, BigDecimal balance,
                                  BigDecimal target, BigDecimal floor, BigDecimal ceiling) {
        CompanyPool pool = mock(CompanyPool.class);
        given(pool.getCurrencyCode()).willReturn(currencyCode);
        given(pool.getBalance()).willReturn(balance);
        given(pool.isBelowFloor()).willReturn(balance.compareTo(floor) < 0);
        given(pool.isAboveCeiling()).willReturn(balance.compareTo(ceiling) > 0);
        given(pool.isWithinThreshold()).willReturn(
                balance.compareTo(floor) >= 0 && balance.compareTo(ceiling) <= 0
        );
        BigDecimal shortage = target.subtract(balance);
        given(pool.shortageToTarget()).willReturn(
                shortage.compareTo(BigDecimal.ZERO) > 0 ? shortage : BigDecimal.ZERO
        );
        BigDecimal surplus = balance.subtract(floor);
        given(pool.surplusAboveFloor()).willReturn(
                surplus.compareTo(BigDecimal.ZERO) > 0 ? surplus : BigDecimal.ZERO
        );
        return pool;
    }
}