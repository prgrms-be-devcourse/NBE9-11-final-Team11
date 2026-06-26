package com.fxflow.domain.companypool.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import com.fxflow.domain.companypool.PoolTestFixtures;
import com.fxflow.domain.companypool.dto.response.RebalancingExecuteRes;
import com.fxflow.domain.companypool.service.AdminAlertService;
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
    @Mock private AdminAlertService adminAlertService;

    @InjectMocks private RebalancingService rebalancingService;

    private static final BigDecimal KRW_TARGET  = PoolTestFixtures.KRW_TARGET;
    private static final BigDecimal KRW_FLOOR   = PoolTestFixtures.KRW_FLOOR;
    private static final BigDecimal KRW_CEILING = PoolTestFixtures.KRW_CEILING;
    private static final BigDecimal USD_TARGET  = PoolTestFixtures.USD_TARGET;
    private static final BigDecimal USD_FLOOR   = PoolTestFixtures.USD_FLOOR;
    private static final BigDecimal USD_CEILING = PoolTestFixtures.USD_CEILING;
    private static final BigDecimal MID_RATE    = PoolTestFixtures.MID_RATE;

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
    @DisplayName("둘 다 floor 미만 → executed=false, reason=BOTH_BELOW_FLOOR, MANUAL_REQUIRED 기록 저장")
    void execute_bothBelowFloor_returnsNotExecuted() {
        givenPools(new BigDecimal("7000000000"), new BigDecimal("5000000")); // 7B, 500만

        RebalancingExecuteRes result = rebalancingService.execute(TriggerType.MANUAL, null);

        assertThat(result.executed()).isFalse();
        assertThat(result.reason()).isEqualTo("BOTH_BELOW_FLOOR");
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
        assertThat(result.action().buyCurrency()).isEqualTo("KRW");
        assertThat(result.action().sellCurrency()).isEqualTo("USD");
        assertThat(result.action().buyAmount()).isEqualByComparingTo("2100000000");
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
        assertThat(result.action().buyCurrency()).isEqualTo("KRW");
        assertThat(result.cappedBy()).isEqualTo("USD_FLOOR");
        assertThat(result.action().buyAmount()).isEqualByComparingTo("1695070000.00");
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
        assertThat(result.action().buyCurrency()).isEqualTo("USD");
        assertThat(result.action().sellCurrency()).isEqualTo("KRW");
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

        assertThat(result.action().buyCurrency()).isEqualTo("KRW");
        assertThat(result.action().sellCurrency()).isEqualTo("USD");
    }

    @Test
    @DisplayName("둘 다 floor 미만 — 관리자 알림 실패해도 MANUAL_REQUIRED audit 기록은 저장됨")
    void execute_bothBelowFloor_alertFails_auditStillSaved() {
        givenPools(new BigDecimal("7000000000"), new BigDecimal("5000000"));
        doThrow(new RuntimeException("알림 전송 실패"))
                .when(adminAlertService).sendBothBelowFloorAlert(any(), any());

        RebalancingExecuteRes result = rebalancingService.execute(TriggerType.MANUAL, null);

        assertThat(result.executed()).isFalse();
        assertThat(result.reason()).isEqualTo("BOTH_BELOW_FLOOR");
        verify(auditService).saveManualRequired(eq(TriggerType.MANUAL), any(), anyString());
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
        given(pool.getFloorBalance()).willReturn(floor);
        return pool;
    }

    // 리밸런싱 후에도 floor 미달 ────────────────────────────────────

    @Test
    @DisplayName("KRW 리밸런싱 후에도 floor 미달 → sendStillBelowFloorAfterRebalancing 호출")
    void execute_krwStillBelowFloorAfterRebalancing_alertSent() {
        // KRW=5B, shortage=5B / USD surplus=1.3M × 1303.9=1.695B → capping
        // buyBalanceAfter=6.695B < floor(8B) → 알림 발화
        givenPools(new BigDecimal("5000000000"), new BigDecimal("6500000"));

        rebalancingService.execute(TriggerType.MANUAL, null);

        verify(adminAlertService).sendStillBelowFloorAfterRebalancing(
                eq("KRW"), any(BigDecimal.class), any(BigDecimal.class));
        verify(rebalancingRepository).save(any(RebalancingOrder.class));
    }

    @Test
    @DisplayName("리밸런싱 후 floor 미달 알림 실패해도 RebalancingOrder 저장됨")
    void execute_stillBelowFloorAlertFails_rebalancingOrderStillSaved() {
        givenPools(new BigDecimal("5000000000"), new BigDecimal("6500000"));
        doThrow(new RuntimeException("알림 전송 실패"))
                .when(adminAlertService).sendStillBelowFloorAfterRebalancing(any(), any(), any());

        rebalancingService.execute(TriggerType.MANUAL, null);

        verify(rebalancingRepository).save(any(RebalancingOrder.class));
    }

    // ── 동시성 ────────────────────────────────────────────────────

    @Test
    @DisplayName("execute() 동시 호출 → 먼저 진입한 스레드만 완료, 나머지는 REBALANCE_IN_PROGRESS")
    void execute_concurrent_onlyOneSucceeds() throws InterruptedException {
        // KRW floor 미만 → 실제 리밸런싱 로직이 실행됨
        givenPools(new BigDecimal("7000000000"), new BigDecimal("6500000"));

        CountDownLatch thread1InProgress = new CountDownLatch(1);
        CountDownLatch releaseThread1 = new CountDownLatch(1);

        // 환율 조회 시점에서 Thread 1을 잡아두어 executing = true 상태를 유지시킨다.
        // Thread 2는 이 시점에 doExecute() 진입을 시도하여 REBALANCE_IN_PROGRESS를 받아야 한다.
        given(exchangeRateProvider.getLatestRate("USD", "KRW")).willAnswer(inv -> {
            thread1InProgress.countDown();
            releaseThread1.await();
            return Optional.of(new FxRateSnapshot(
                    "USD", "KRW", MID_RATE, new BigDecimal("0.01"), java.time.LocalDateTime.now()));
        });

        AtomicReference<RebalancingExecuteRes> thread1Result = new AtomicReference<>();
        AtomicReference<Exception> thread1Error = new AtomicReference<>();
        AtomicReference<BusinessException> thread2Error = new AtomicReference<>();

        Thread thread1 = new Thread(() -> {
            try {
                thread1Result.set(rebalancingService.execute(TriggerType.MANUAL, null));
            } catch (Exception e) {
                thread1Error.set(e);
            }
        });
        thread1.start();
        thread1InProgress.await(); // Thread 1이 환율 조회에서 블로킹 중 (executing = true 보유)

        // 현재 테스트 스레드(Thread 2)가 동시 진입 시도
        try {
            rebalancingService.execute(TriggerType.MANUAL, null);
        } catch (BusinessException e) {
            thread2Error.set(e);
        }

        releaseThread1.countDown(); // Thread 1 계속 진행
        thread1.join();

        // Thread 2는 REBALANCE_IN_PROGRESS를 받아야 함
        assertThat(thread2Error.get()).isNotNull();
        assertThat(thread2Error.get().getErrorCode()).isEqualTo(PoolErrorCode.REBALANCE_IN_PROGRESS);

        // Thread 1은 정상 완료
        assertThat(thread1Error.get()).isNull();
        assertThat(thread1Result.get().executed()).isTrue();
    }
}