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

    private static final BigDecimal KRW_TARGET     = PoolTestFixtures.KRW_TARGET;
    private static final BigDecimal KRW_SAFE_FLOOR = PoolTestFixtures.KRW_SAFE_FLOOR;
    private static final BigDecimal KRW_FLOOR      = PoolTestFixtures.KRW_FLOOR;
    private static final BigDecimal KRW_CEILING    = PoolTestFixtures.KRW_CEILING;
    private static final BigDecimal USD_TARGET     = PoolTestFixtures.USD_TARGET;
    private static final BigDecimal USD_SAFE_FLOOR = PoolTestFixtures.USD_SAFE_FLOOR;
    private static final BigDecimal USD_FLOOR      = PoolTestFixtures.USD_FLOOR;
    private static final BigDecimal USD_CEILING    = PoolTestFixtures.USD_CEILING;
    private static final BigDecimal MID_RATE       = PoolTestFixtures.MID_RATE;

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
    @DisplayName("둘 다 floor(60%) 미만 → executed=false, reason=BOTH_BELOW_FLOOR, MANUAL_REQUIRED 기록 저장")
    void execute_bothBelowFloor_returnsNotExecuted() {
        // KRW=5B < 6B floor, USD=3M < 3.9M floor
        givenPools(new BigDecimal("5000000000"), new BigDecimal("3000000"));

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
    @DisplayName("KRW floor(60%) 미만 — 완전 체결 (부족분 < USD safeFloor 여유분, cappedBy = null)")
    void execute_krwBelowFloor_fullFill() {
        // KRW: 5.5B → shortageToSafeFloor = 8B - 5.5B = 2.5B
        // USD: 9M  → surplusAboveSafeFloor = 9M - 5.2M = 3.8M
        // appliedRate = 1300 × 1.002 = 1302.6
        // maxBuyableKRW = 3,800,000 × 1302.6 = 4,949,880,000
        // 2.5B < 4.95B → 완전 체결, buyAmount = 2,500,000,000
        givenPools(new BigDecimal("5500000000"), new BigDecimal("9000000")); // KRW 5.5B, USD 9M

        RebalancingExecuteRes result = rebalancingService.execute(TriggerType.MANUAL, null);

        assertThat(result.executed()).isTrue();
        assertThat(result.action().buyCurrency()).isEqualTo("KRW");
        assertThat(result.action().sellCurrency()).isEqualTo("USD");
        assertThat(result.action().buyAmount()).isEqualByComparingTo("2500000000");
        assertThat(result.cappedBy()).isNull();
        verify(companyPoolRepository).increaseBalance(eq("KRW"), any());
        verify(companyPoolRepository).decreaseBalance(eq("USD"), any());
        verify(rebalancingRepository).save(any(RebalancingOrder.class));
    }

    @Test
    @DisplayName("KRW floor(60%) 미만 — 부분 체결 (부족분 > USD safeFloor 여유분, cappedBy = USD_FLOOR)")
    void execute_krwBelowFloor_partialFill_cappedByUsdFloor() {
        // KRW: 5.5B → shortageToSafeFloor = 2.5B
        // USD: 7M  → surplusAboveSafeFloor = 7M - 5.2M = 1.8M
        // maxBuyableKRW = 1,800,000 × 1302.6 = 2,344,680,000
        // 2.5B > 2.344B → 부분 체결, cappedBy = USD_FLOOR
        givenPools(new BigDecimal("5500000000"), new BigDecimal("7000000"));

        RebalancingExecuteRes result = rebalancingService.execute(TriggerType.MANUAL, null);

        assertThat(result.executed()).isTrue();
        assertThat(result.action().buyCurrency()).isEqualTo("KRW");
        assertThat(result.cappedBy()).isEqualTo("USD_FLOOR");
        assertThat(result.action().buyAmount()).isEqualByComparingTo("2344680000");
        verify(rebalancingRepository).save(any(RebalancingOrder.class));
    }

    @Test
    @DisplayName("USD floor(60%) 미만, KRW 정상 → USD 매입, KRW 매도")
    void execute_usdBelowFloor_buyUsd() {
        // USD: 3M < 3.9M floor
        givenPools(new BigDecimal("10000000000"), new BigDecimal("3000000"));

        RebalancingExecuteRes result = rebalancingService.execute(TriggerType.MANUAL, null);

        assertThat(result.executed()).isTrue();
        assertThat(result.action().buyCurrency()).isEqualTo("USD");
        assertThat(result.action().sellCurrency()).isEqualTo("KRW");
        verify(companyPoolRepository).increaseBalance(eq("USD"), any());
        verify(companyPoolRepository).decreaseBalance(eq("KRW"), any());
    }

    @Test
    @DisplayName("KRW floor(60%) 미만 + USD ceiling 초과 → floor 우선, KRW 매입 (USD safeFloor 여유분 있음)")
    void execute_krwBelowFloor_usdAboveCeiling_floorPriority() {
        // KRW: 5B < 6B floor
        // USD: 8M > 7.8M ceiling
        givenPools(new BigDecimal("5000000000"), new BigDecimal("8000000"));

        RebalancingExecuteRes result = rebalancingService.execute(TriggerType.MANUAL, null);

        assertThat(result.action().buyCurrency()).isEqualTo("KRW");
        assertThat(result.action().sellCurrency()).isEqualTo("USD");
    }

    @Test
    @DisplayName("둘 다 floor(60%) 미만 — 관리자 알림 실패해도 MANUAL_REQUIRED audit 기록은 저장됨")
    void execute_bothBelowFloor_alertFails_auditStillSaved() {
        givenPools(new BigDecimal("5000000000"), new BigDecimal("3000000"));
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
        givenPools(new BigDecimal("5000000000"), new BigDecimal("6500000")); // KRW floor(60%) 미만
        given(exchangeRateProvider.getLatestRate("USD", "KRW")).willReturn(Optional.empty());

        assertThatThrownBy(() -> rebalancingService.execute(TriggerType.MANUAL, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(PoolErrorCode.RATE_UNAVAILABLE));
    }

    // 헬퍼 ───────────────────────────────────────────────────────

    private void givenPools(BigDecimal krwBalance, BigDecimal usdBalance) {
        CompanyPool krwPool = mockPool("KRW", krwBalance, KRW_SAFE_FLOOR, KRW_FLOOR, KRW_CEILING);
        CompanyPool usdPool = mockPool("USD", usdBalance, USD_SAFE_FLOOR, USD_FLOOR, USD_CEILING);
        given(companyPoolRepository.findByCurrencyCodeWithLock("KRW")).willReturn(Optional.of(krwPool));
        given(companyPoolRepository.findByCurrencyCodeWithLock("USD")).willReturn(Optional.of(usdPool));
    }

    private CompanyPool mockPool(String currencyCode, BigDecimal balance,
                                  BigDecimal safeFloor, BigDecimal floor, BigDecimal ceiling) {
        CompanyPool pool = mock(CompanyPool.class);
        given(pool.getCurrencyCode()).willReturn(currencyCode);
        given(pool.getBalance()).willReturn(balance);
        given(pool.isBelowFloor()).willReturn(balance.compareTo(floor) < 0);
        given(pool.isAboveCeiling()).willReturn(balance.compareTo(ceiling) > 0);
        given(pool.isWithinThreshold()).willReturn(
                balance.compareTo(floor) >= 0 && balance.compareTo(ceiling) <= 0
        );
        BigDecimal shortage = safeFloor.subtract(balance);
        given(pool.shortageToSafeFloor()).willReturn(
                shortage.compareTo(BigDecimal.ZERO) > 0 ? shortage : BigDecimal.ZERO
        );
        BigDecimal surplus = balance.subtract(safeFloor);
        given(pool.surplusAboveSafeFloor()).willReturn(
                surplus.compareTo(BigDecimal.ZERO) > 0 ? surplus : BigDecimal.ZERO
        );
        given(pool.getFloorBalance()).willReturn(floor);
        return pool;
    }

    // 리밸런싱 후에도 floor 미달 ────────────────────────────────────

    @Test
    @DisplayName("KRW 리밸런싱 후에도 floor(60%) 미달 → sendStillBelowFloorAfterRebalancing 호출")
    void execute_krwStillBelowFloorAfterRebalancing_alertSent() {
        // KRW=1B: shortage=7B / USD surplus(safeFloor기준)=1.3M × 1302.6=1.693B → capping
        // buyBalanceAfter = 1B + 1.693B = 2.693B < floor(6B) → 알림 발화
        givenPools(new BigDecimal("1000000000"), new BigDecimal("6500000"));

        rebalancingService.execute(TriggerType.MANUAL, null);

        verify(adminAlertService).sendStillBelowFloorAfterRebalancing(
                eq("KRW"), any(BigDecimal.class), any(BigDecimal.class));
        verify(rebalancingRepository).save(any(RebalancingOrder.class));
    }

    @Test
    @DisplayName("리밸런싱 후 floor 미달 알림 실패해도 RebalancingOrder 저장됨")
    void execute_stillBelowFloorAlertFails_rebalancingOrderStillSaved() {
        givenPools(new BigDecimal("1000000000"), new BigDecimal("6500000"));
        doThrow(new RuntimeException("알림 전송 실패"))
                .when(adminAlertService).sendStillBelowFloorAfterRebalancing(any(), any(), any());

        rebalancingService.execute(TriggerType.MANUAL, null);

        verify(rebalancingRepository).save(any(RebalancingOrder.class));
    }

    // ── 동시성 ────────────────────────────────────────────────────

    @Test
    @DisplayName("execute() 동시 호출 → 먼저 진입한 스레드만 완료, 나머지는 REBALANCE_IN_PROGRESS")
    void execute_concurrent_onlyOneSucceeds() throws InterruptedException {
        // KRW floor(60%) 미만 → 실제 리밸런싱 로직이 실행됨 (환율 조회까지 진행)
        givenPools(new BigDecimal("5000000000"), new BigDecimal("6500000"));

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