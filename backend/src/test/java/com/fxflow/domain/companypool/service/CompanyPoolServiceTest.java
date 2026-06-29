package com.fxflow.domain.companypool.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fxflow.domain.companypool.PoolTestFixtures;
import com.fxflow.domain.companypool.dto.PoolChange;
import com.fxflow.domain.companypool.dto.response.PoolDashboardRes;
import com.fxflow.domain.companypool.entity.CompanyPool;
import com.fxflow.domain.companypool.errorcode.PoolErrorCode;
import com.fxflow.domain.companypool.event.PoolChangedEvent;
import com.fxflow.domain.companypool.repository.CompanyPoolRepository;
import com.fxflow.domain.ledger.repository.LedgerEntryRepository;
import com.fxflow.global.exception.BusinessException;
import com.fxflow.global.fx.ExchangeRateProvider;
import com.fxflow.global.fx.FxRateSnapshot;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CompanyPoolServiceTest {

    @Mock private CompanyPoolRepository companyPoolRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private LedgerEntryRepository ledgerEntryRepository;
    @Mock private ExchangeRateProvider exchangeRateProvider;

    @InjectMocks private CompanyPoolService companyPoolService;

    @Test
    @DisplayName("increase → increaseBalance 호출, 이벤트 발행")
    void apply_increase_callsIncreaseAndPublishesEvent() {
        List<PoolChange> changes = List.of(PoolChange.increase("KRW", new BigDecimal("1000000")));

        companyPoolService.apply(changes);

        verify(companyPoolRepository).increaseBalance("KRW", new BigDecimal("1000000"));
        verify(companyPoolRepository, never()).decreaseBalance(any(), any());
        verify(eventPublisher).publishEvent(any(PoolChangedEvent.class));
    }

    @Test
    @DisplayName("decrease 성공 → decreaseBalance 호출, 이벤트 발행")
    void apply_decrease_success_publishesEvent() {
        given(companyPoolRepository.decreaseBalance(eq("USD"), any())).willReturn(1);
        List<PoolChange> changes = List.of(PoolChange.decrease("USD", new BigDecimal("1000")));

        companyPoolService.apply(changes);

        verify(companyPoolRepository).decreaseBalance("USD", new BigDecimal("1000"));
        verify(companyPoolRepository, never()).increaseBalance(any(), any());
        verify(eventPublisher).publishEvent(any(PoolChangedEvent.class));
    }

    @Test
    @DisplayName("해외송금: KRW 증가 + USD 감소 → 각각 호출, 이벤트 한 번 발행")
    void apply_remittance_krwIncreaseUsdDecrease_publishesEventOnce() {
        given(companyPoolRepository.decreaseBalance(eq("USD"), any())).willReturn(1);
        List<PoolChange> changes = List.of(
                PoolChange.increase("KRW", new BigDecimal("1300000")),
                PoolChange.decrease("USD", new BigDecimal("1000"))
        );

        companyPoolService.apply(changes);

        verify(companyPoolRepository).increaseBalance("KRW", new BigDecimal("1300000"));
        verify(companyPoolRepository).decreaseBalance("USD", new BigDecimal("1000"));
        verify(eventPublisher, times(1)).publishEvent(any(PoolChangedEvent.class));
    }

    @Test
    @DisplayName("decrease 잔액 부족 → POOL_INSUFFICIENT_BALANCE 예외, 이벤트 미발행")
    void apply_decrease_insufficient_throwsExceptionAndNoEvent() {
        given(companyPoolRepository.decreaseBalance(eq("USD"), any())).willReturn(0);
        List<PoolChange> changes = List.of(PoolChange.decrease("USD", new BigDecimal("99999999")));

        assertThatThrownBy(() -> companyPoolService.apply(changes))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(PoolErrorCode.POOL_INSUFFICIENT_BALANCE));

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("첫 번째 증가 성공 후 두 번째 감소 실패 → 예외 발생, 이벤트 미발행")
    void apply_firstSuccessSecondFails_throwsExceptionAndNoEvent() {
        given(companyPoolRepository.decreaseBalance(eq("USD"), any())).willReturn(0);
        List<PoolChange> changes = List.of(
                PoolChange.increase("KRW", new BigDecimal("1300000")),
                PoolChange.decrease("USD", new BigDecimal("99999999"))
        );

        assertThatThrownBy(() -> companyPoolService.apply(changes))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(PoolErrorCode.POOL_INSUFFICIENT_BALANCE));

        verify(companyPoolRepository).increaseBalance(eq("KRW"), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    // ── deposit() 테스트 ─────────────────────────────────────────

    @Test
    @DisplayName("deposit 성공 시 PoolChangedEvent 발행 → 리밸런싱 체크 트리거")
    void deposit_success_publishesPoolChangedEvent() {
        CompanyPool pool = mockPoolForDeposit();
        given(companyPoolRepository.findByCurrencyCodeWithLock("KRW")).willReturn(Optional.of(pool));

        companyPoolService.deposit("journal-001", "KRW", new BigDecimal("1000000"));

        verify(eventPublisher).publishEvent(any(PoolChangedEvent.class));
    }

    @Test
    @DisplayName("deposit - 원장 저장 실패 시 예외 전파 + 이벤트 미발행")
    void deposit_ledgerSaveFails_exceptionPropagatesAndEventNotPublished() {
        CompanyPool pool = mockPoolForDeposit();
        given(companyPoolRepository.findByCurrencyCodeWithLock("KRW")).willReturn(Optional.of(pool));
        doThrow(new RuntimeException("DB 장애")).when(ledgerEntryRepository).save(any());

        assertThatThrownBy(() -> companyPoolService.deposit("journal-001", "KRW", new BigDecimal("1000000")))
                .isInstanceOf(RuntimeException.class);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("deposit - 통화 풀이 존재하지 않으면 POOL_NOT_FOUND 예외")
    void deposit_poolNotFound_throwsPoolNotFound() {
        given(companyPoolRepository.findByCurrencyCodeWithLock("USD")).willReturn(Optional.empty());

        assertThatThrownBy(() -> companyPoolService.deposit("journal-001", "USD", new BigDecimal("1000")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(PoolErrorCode.POOL_NOT_FOUND));
    }

    // ── withdraw() 테스트 ────────────────────────────────────────

    @Test
    @DisplayName("withdraw 성공 시 PoolChangedEvent 발행 → 리밸런싱 체크 트리거")
    void withdraw_success_publishesPoolChangedEvent() {
        CompanyPool pool = mockPoolForWithdraw(new BigDecimal("10000000000"));
        given(companyPoolRepository.findByCurrencyCodeWithLock("KRW")).willReturn(Optional.of(pool));

        companyPoolService.withdraw("journal-001", "KRW", new BigDecimal("1000000"));

        verify(eventPublisher).publishEvent(any(PoolChangedEvent.class));
    }

    @Test
    @DisplayName("withdraw - 출금 후 풀 잔액이 0 미만이면 POOL_INSUFFICIENT_BALANCE 예외")
    void withdraw_exceedsPoolBalance_throwsInsufficientBalance() {
        CompanyPool pool = mockPoolForWithdraw(new BigDecimal("500"));
        given(companyPoolRepository.findByCurrencyCodeWithLock("KRW")).willReturn(Optional.of(pool));

        assertThatThrownBy(() -> companyPoolService.withdraw("journal-001", "KRW", new BigDecimal("1000")))
                .isInstanceOf(BusinessException.class).satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(PoolErrorCode.POOL_INSUFFICIENT_BALANCE));
    }

    @Test
    @DisplayName("withdraw - 잔액 부족 시 이벤트 미발행 (실패 거래는 리밸런싱 미트리거)")
    void withdraw_exceedsPoolBalance_eventNotPublished() {
        CompanyPool pool = mockPoolForWithdraw(new BigDecimal("500"));
        given(companyPoolRepository.findByCurrencyCodeWithLock("KRW")).willReturn(Optional.of(pool));

        assertThatThrownBy(() -> companyPoolService.withdraw("journal-001", "KRW", new BigDecimal("1000")))
                .isInstanceOf(BusinessException.class);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("withdraw - 출금 금액이 잔액과 정확히 같으면 성공 (경계값 0원)")
    void withdraw_exactBalance_success() {
        CompanyPool pool = mockPoolForWithdraw(new BigDecimal("1000"));
        given(companyPoolRepository.findByCurrencyCodeWithLock("KRW")).willReturn(Optional.of(pool));

        companyPoolService.withdraw("journal-001", "KRW", new BigDecimal("1000"));

        verify(eventPublisher).publishEvent(any(PoolChangedEvent.class));
    }

    private CompanyPool mockPoolForDeposit() {
        CompanyPool pool = mock(CompanyPool.class);
        given(pool.getId()).willReturn(1L);
        given(pool.getCurrencyCode()).willReturn("KRW");
        given(pool.getBalance()).willReturn(new BigDecimal("10000000000"));
        return pool;
    }

    private CompanyPool mockPoolForWithdraw(BigDecimal balance) {
        CompanyPool pool = mock(CompanyPool.class);
        given(pool.getId()).willReturn(1L);
        given(pool.getCurrencyCode()).willReturn("KRW");
        given(pool.getBalance()).willReturn(balance);
        return pool;
    }

    // ── getDashboard() 테스트 ─────────────────────────────────────

    @Test
    @DisplayName("KRW floor(60%) 미만 → safeFloor 기준 amount cap, counterAmount 계산")
    void getDashboard_krwBelowFloor_correctStatusAndAction() {
        // KRW: 5B (< 6B floor=60%) — shortageToSafeFloor = 3B
        // USD: target=6.5M — surplusAboveSafeFloor = 6.5M - 5.2M = 1.3M
        // appliedRate = 1300 × 1.002 = 1302.6
        // maxBuyableKRW = 1,300,000 × 1302.6 = 1,693,380,000 → cap 적용
        // counterAmount = 1,693,380,000 / 1302.6 = 1,300,000 (정확히 떨어짐)
        BigDecimal krwBalance = new BigDecimal("5000000000");
        CompanyPool krwPool = mockPoolFor("KRW", krwBalance,
                PoolTestFixtures.KRW_TARGET, PoolTestFixtures.KRW_SAFE_FLOOR,
                PoolTestFixtures.KRW_FLOOR, PoolTestFixtures.KRW_CEILING);
        CompanyPool usdPool = mockPoolFor("USD", PoolTestFixtures.USD_TARGET,
                PoolTestFixtures.USD_TARGET, PoolTestFixtures.USD_SAFE_FLOOR,
                PoolTestFixtures.USD_FLOOR, PoolTestFixtures.USD_CEILING);
        given(companyPoolRepository.findByCurrencyCode("KRW")).willReturn(Optional.of(krwPool));
        given(companyPoolRepository.findByCurrencyCode("USD")).willReturn(Optional.of(usdPool));
        given(exchangeRateProvider.getLatestRate("USD", "KRW")).willReturn(
                Optional.of(new FxRateSnapshot("USD", "KRW", PoolTestFixtures.MID_RATE, new BigDecimal("0.01"), LocalDateTime.now())));

        PoolDashboardRes result = companyPoolService.getDashboard();

        assertThat(result.asOf()).isNotNull();
        PoolDashboardRes.PoolStatusRes krw = result.pools().getFirst();
        assertThat(krw.status()).isEqualTo("BELOW_FLOOR");
        assertThat(krw.utilizationRate()).isEqualByComparingTo("0.5000");
        assertThat(krw.recommendedAction().type()).isEqualTo("BUY");
        assertThat(krw.recommendedAction().amount()).isEqualByComparingTo("1693380000");
        assertThat(krw.recommendedAction().counterAmount()).isEqualByComparingTo("1300000");
    }

    @Test
    @DisplayName("KRW ceiling 초과 → amount=초과분 3B, counterAmount=3B÷1302.6 (버림, 8자리)")
    void getDashboard_krwAboveCeiling_correctStatusAndAction() {
        // counterAmount = 3,000,000,000 / 1302.6 = 2,303,086.13542146...
        BigDecimal krwBalance = new BigDecimal("13000000000");
        CompanyPool krwPool = mockPoolFor("KRW", krwBalance,
                PoolTestFixtures.KRW_TARGET, PoolTestFixtures.KRW_SAFE_FLOOR,
                PoolTestFixtures.KRW_FLOOR, PoolTestFixtures.KRW_CEILING);
        CompanyPool usdPool = mockPoolFor("USD", PoolTestFixtures.USD_TARGET,
                PoolTestFixtures.USD_TARGET, PoolTestFixtures.USD_SAFE_FLOOR,
                PoolTestFixtures.USD_FLOOR, PoolTestFixtures.USD_CEILING);
        given(companyPoolRepository.findByCurrencyCode("KRW")).willReturn(Optional.of(krwPool));
        given(companyPoolRepository.findByCurrencyCode("USD")).willReturn(Optional.of(usdPool));
        given(exchangeRateProvider.getLatestRate("USD", "KRW")).willReturn(
                Optional.of(new FxRateSnapshot("USD", "KRW", PoolTestFixtures.MID_RATE, new BigDecimal("0.01"), LocalDateTime.now())));

        PoolDashboardRes result = companyPoolService.getDashboard();

        PoolDashboardRes.PoolStatusRes krw = result.pools().getFirst();
        assertThat(krw.status()).isEqualTo("ABOVE_CEILING");
        assertThat(krw.utilizationRate()).isEqualByComparingTo("1.3000");
        assertThat(krw.recommendedAction().type()).isEqualTo("SELL");
        assertThat(krw.recommendedAction().amount()).isEqualByComparingTo("3000000000");
        assertThat(krw.recommendedAction().counterAmount()).isEqualByComparingTo("2303086.13542146");
    }

    @Test
    @DisplayName("양 통화 모두 정상 범위 → status=NORMAL, recommendedAction=null")
    void getDashboard_bothNormal_noRecommendedAction() {
        CompanyPool krwPool = mockPoolFor("KRW", PoolTestFixtures.KRW_TARGET,
                PoolTestFixtures.KRW_TARGET, PoolTestFixtures.KRW_SAFE_FLOOR,
                PoolTestFixtures.KRW_FLOOR, PoolTestFixtures.KRW_CEILING);
        CompanyPool usdPool = mockPoolFor("USD", PoolTestFixtures.USD_TARGET,
                PoolTestFixtures.USD_TARGET, PoolTestFixtures.USD_SAFE_FLOOR,
                PoolTestFixtures.USD_FLOOR, PoolTestFixtures.USD_CEILING);
        given(companyPoolRepository.findByCurrencyCode("KRW")).willReturn(Optional.of(krwPool));
        given(companyPoolRepository.findByCurrencyCode("USD")).willReturn(Optional.of(usdPool));
        given(exchangeRateProvider.getLatestRate("USD", "KRW")).willReturn(Optional.empty());

        PoolDashboardRes result = companyPoolService.getDashboard();

        assertThat(result.asOf()).isNotNull();
        result.pools().forEach(p -> {
            assertThat(p.status()).isEqualTo("NORMAL");
            assertThat(p.recommendedAction()).isNull();
        });
    }

    @Test
    @DisplayName("환율 조회 실패 시 counterAmount=null, 대시보드는 정상 반환")
    void getDashboard_rateFetchFails_counterAmountNull() {
        // KRW: 5B (< 6B floor=60%)
        BigDecimal krwBalance = new BigDecimal("5000000000");
        CompanyPool krwPool = mockPoolFor("KRW", krwBalance,
                PoolTestFixtures.KRW_TARGET, PoolTestFixtures.KRW_SAFE_FLOOR,
                PoolTestFixtures.KRW_FLOOR, PoolTestFixtures.KRW_CEILING);
        CompanyPool usdPool = mockPoolFor("USD", PoolTestFixtures.USD_TARGET,
                PoolTestFixtures.USD_TARGET, PoolTestFixtures.USD_SAFE_FLOOR,
                PoolTestFixtures.USD_FLOOR, PoolTestFixtures.USD_CEILING);
        given(companyPoolRepository.findByCurrencyCode("KRW")).willReturn(Optional.of(krwPool));
        given(companyPoolRepository.findByCurrencyCode("USD")).willReturn(Optional.of(usdPool));
        given(exchangeRateProvider.getLatestRate("USD", "KRW")).willReturn(Optional.empty());

        PoolDashboardRes result = companyPoolService.getDashboard();

        PoolDashboardRes.PoolStatusRes krw = result.pools().getFirst();
        assertThat(krw.status()).isEqualTo("BELOW_FLOOR");
        assertThat(krw.recommendedAction().amount()).isNotNull();
        assertThat(krw.recommendedAction().counterAmount()).isNull();
    }

    private CompanyPool mockPoolFor(String currencyCode, BigDecimal balance,
                                    BigDecimal target, BigDecimal safeFloor,
                                    BigDecimal floor, BigDecimal ceiling) {
        CompanyPool pool = mock(CompanyPool.class);
        given(pool.getCurrencyCode()).willReturn(currencyCode);
        given(pool.getBalance()).willReturn(balance);
        given(pool.getTargetBalance()).willReturn(target);
        given(pool.getFloorBalance()).willReturn(floor);
        given(pool.getSafeFloorBalance()).willReturn(safeFloor);
        given(pool.getCeilingBalance()).willReturn(ceiling);
        given(pool.isBelowFloor()).willReturn(balance.compareTo(floor) < 0);
        given(pool.isAboveCeiling()).willReturn(balance.compareTo(ceiling) > 0);
        BigDecimal shortage = safeFloor.subtract(balance);
        given(pool.shortageToSafeFloor()).willReturn(
                shortage.compareTo(BigDecimal.ZERO) > 0 ? shortage : BigDecimal.ZERO);
        BigDecimal surplus = balance.subtract(safeFloor);
        given(pool.surplusAboveSafeFloor()).willReturn(
                surplus.compareTo(BigDecimal.ZERO) > 0 ? surplus : BigDecimal.ZERO);
        return pool;
    }
}