package com.fxflow.domain.companypool.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fxflow.domain.companypool.dto.PoolChange;
import com.fxflow.domain.companypool.errorcode.PoolErrorCode;
import com.fxflow.domain.companypool.event.PoolChangedEvent;
import com.fxflow.domain.companypool.repository.CompanyPoolRepository;
import com.fxflow.global.exception.BusinessException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class CompanyPoolServiceTest {

    @Mock private CompanyPoolRepository companyPoolRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

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
}