package com.fxflow.domain.companypool.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
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
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

    // ── getDashboard() 테스트 ────────────────────────────────────

    @Test
    @DisplayName("KRW floor 미만 → status=BELOW_FLOOR, utilizationRate=0.7000, recommendedAction={BUY, 3B}")
    void getDashboard_krwBelowFloor_correctStatusAndAction() {
        BigDecimal krwBalance = new BigDecimal("7000000000");
        CompanyPool krwPool = mockPoolFor("KRW", krwBalance,
                PoolTestFixtures.KRW_TARGET, PoolTestFixtures.KRW_FLOOR, PoolTestFixtures.KRW_CEILING);
        CompanyPool usdPool = mockPoolFor("USD", PoolTestFixtures.USD_TARGET,
                PoolTestFixtures.USD_TARGET, PoolTestFixtures.USD_FLOOR, PoolTestFixtures.USD_CEILING);
        given(companyPoolRepository.findByCurrencyCode("KRW")).willReturn(Optional.of(krwPool));
        given(companyPoolRepository.findByCurrencyCode("USD")).willReturn(Optional.of(usdPool));

        PoolDashboardRes result = companyPoolService.getDashboard();

        assertThat(result.asOf()).isNotNull();
        PoolDashboardRes.PoolStatusRes krw = result.pools().get(0);
        assertThat(krw.status()).isEqualTo("BELOW_FLOOR");
        assertThat(krw.utilizationRate()).isEqualByComparingTo("0.7000");
        assertThat(krw.recommendedAction().type()).isEqualTo("BUY");
        assertThat(krw.recommendedAction().amount()).isEqualByComparingTo("3000000000");
    }

    @Test
    @DisplayName("KRW ceiling 초과 → status=ABOVE_CEILING, utilizationRate=1.3000, recommendedAction={SELL, 3B}")
    void getDashboard_krwAboveCeiling_correctStatusAndAction() {
        BigDecimal krwBalance = new BigDecimal("13000000000");
        CompanyPool krwPool = mockPoolFor("KRW", krwBalance,
                PoolTestFixtures.KRW_TARGET, PoolTestFixtures.KRW_FLOOR, PoolTestFixtures.KRW_CEILING);
        CompanyPool usdPool = mockPoolFor("USD", PoolTestFixtures.USD_TARGET,
                PoolTestFixtures.USD_TARGET, PoolTestFixtures.USD_FLOOR, PoolTestFixtures.USD_CEILING);
        given(companyPoolRepository.findByCurrencyCode("KRW")).willReturn(Optional.of(krwPool));
        given(companyPoolRepository.findByCurrencyCode("USD")).willReturn(Optional.of(usdPool));

        PoolDashboardRes result = companyPoolService.getDashboard();

        PoolDashboardRes.PoolStatusRes krw = result.pools().get(0);
        assertThat(krw.status()).isEqualTo("ABOVE_CEILING");
        assertThat(krw.utilizationRate()).isEqualByComparingTo("1.3000");
        assertThat(krw.recommendedAction().type()).isEqualTo("SELL");
        assertThat(krw.recommendedAction().amount()).isEqualByComparingTo("3000000000");
    }

    @Test
    @DisplayName("양 통화 모두 정상 범위 → status=NORMAL, recommendedAction=null")
    void getDashboard_bothNormal_noRecommendedAction() {
        CompanyPool krwPool = mockPoolFor("KRW", PoolTestFixtures.KRW_TARGET,
                PoolTestFixtures.KRW_TARGET, PoolTestFixtures.KRW_FLOOR, PoolTestFixtures.KRW_CEILING);
        CompanyPool usdPool = mockPoolFor("USD", PoolTestFixtures.USD_TARGET,
                PoolTestFixtures.USD_TARGET, PoolTestFixtures.USD_FLOOR, PoolTestFixtures.USD_CEILING);
        given(companyPoolRepository.findByCurrencyCode("KRW")).willReturn(Optional.of(krwPool));
        given(companyPoolRepository.findByCurrencyCode("USD")).willReturn(Optional.of(usdPool));

        PoolDashboardRes result = companyPoolService.getDashboard();

        assertThat(result.asOf()).isNotNull();
        result.pools().forEach(p -> {
            assertThat(p.status()).isEqualTo("NORMAL");
            assertThat(p.recommendedAction()).isNull();
        });
    }

    private CompanyPool mockPoolFor(String currencyCode, BigDecimal balance,
                                    BigDecimal target, BigDecimal floor, BigDecimal ceiling) {
        CompanyPool pool = mock(CompanyPool.class);
        given(pool.getCurrencyCode()).willReturn(currencyCode);
        given(pool.getBalance()).willReturn(balance);
        given(pool.getTargetBalance()).willReturn(target);
        given(pool.getFloorBalance()).willReturn(floor);
        given(pool.getCeilingBalance()).willReturn(ceiling);
        given(pool.isBelowFloor()).willReturn(balance.compareTo(floor) < 0);
        given(pool.isAboveCeiling()).willReturn(balance.compareTo(ceiling) > 0);
        // shortageToTarget은 BELOW_FLOOR일 때만 호출됨
        BigDecimal shortage = target.subtract(balance);
        given(pool.shortageToTarget()).willReturn(
                shortage.compareTo(BigDecimal.ZERO) > 0 ? shortage : BigDecimal.ZERO);
        return pool;
    }
}