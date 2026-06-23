package com.fxflow.domain.reservation.listener;

import com.fxflow.domain.reservation.entity.Reservation;
import com.fxflow.domain.reservation.enums.ReservationAction;
import com.fxflow.domain.reservation.enums.ReservationStatus;
import com.fxflow.domain.reservation.repository.ReservationRepository;
import com.fxflow.domain.reservation.service.ReservationExecutionService;
import com.fxflow.global.fx.FxRateSnapshot;
import com.fxflow.global.fx.FxRateUpdatedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationTriggerListenerTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private ReservationExecutionService reservationExecutionService;

    @InjectMocks
    private ReservationTriggerListener reservationTriggerListener;

    private static final BigDecimal AMOUNT = new BigDecimal("1000000");
    private static final BigDecimal TARGET = new BigDecimal("1300");
    private static final LocalDateTime FUTURE = LocalDateTime.now().plusDays(7);
    private static final LocalDateTime PAST = LocalDateTime.now().minusDays(1);

    private FxRateUpdatedEvent event(String base, String quote, String mid, String spread) {
        return new FxRateUpdatedEvent(new FxRateSnapshot(
                base, quote, new BigDecimal(mid), new BigDecimal(spread), LocalDateTime.now()));
    }

    private Reservation reservation(Long id, String from, String to, LocalDateTime expiresAt) {
        Reservation reservation = Reservation.createExchange(1L, from, to, AMOUNT, TARGET, expiresAt, "k");
        ReflectionTestUtils.setField(reservation, "id", id);
        return reservation;
    }

    @Test
    @DisplayName("매수(KRW→USD) — 적용 매수가가 목표 이하이면 선점·체결을 시도한다")
    void buy_reached_executes() {
        Reservation reservation = reservation(10L, "KRW", "USD", FUTURE);
        when(reservationRepository.findByStatusAndAction(ReservationStatus.ACTIVE, ReservationAction.EXCHANGE))
                .thenReturn(List.of(reservation));
        when(reservationExecutionService.preempt(10L)).thenReturn(true);

        // mid=1280, spread=0.01 → buyRate=1292.80 ≤ 1300
        reservationTriggerListener.onFxRateUpdated(event("USD", "KRW", "1280", "0.01"));

        verify(reservationExecutionService).preempt(10L);
        verify(reservationExecutionService).executeTriggered(10L);
    }

    @Test
    @DisplayName("매수 — 적용 매수가가 목표 초과면 체결하지 않는다")
    void buy_notReached_skips() {
        Reservation reservation = reservation(10L, "KRW", "USD", FUTURE);
        when(reservationRepository.findByStatusAndAction(ReservationStatus.ACTIVE, ReservationAction.EXCHANGE))
                .thenReturn(List.of(reservation));

        // mid=1300, spread=0.01 → buyRate=1313.00 > 1300
        reservationTriggerListener.onFxRateUpdated(event("USD", "KRW", "1300", "0.01"));

        verify(reservationExecutionService, never()).preempt(anyLong());
    }

    @Test
    @DisplayName("매도(USD→KRW) — 적용 매도가가 목표 이상이면 선점·체결을 시도한다")
    void sell_reached_executes() {
        Reservation reservation = reservation(11L, "USD", "KRW", FUTURE);
        when(reservationRepository.findByStatusAndAction(ReservationStatus.ACTIVE, ReservationAction.EXCHANGE))
                .thenReturn(List.of(reservation));
        when(reservationExecutionService.preempt(11L)).thenReturn(true);

        // mid=1320, spread=0.01 → sellRate=1306.80 ≥ 1300
        reservationTriggerListener.onFxRateUpdated(event("USD", "KRW", "1320", "0.01"));

        verify(reservationExecutionService).preempt(11L);
        verify(reservationExecutionService).executeTriggered(11L);
    }

    @Test
    @DisplayName("만료된 예약은 도달해도 체결하지 않는다")
    void expired_skips() {
        Reservation reservation = reservation(10L, "KRW", "USD", PAST);
        when(reservationRepository.findByStatusAndAction(ReservationStatus.ACTIVE, ReservationAction.EXCHANGE))
                .thenReturn(List.of(reservation));

        // 도달 조건이지만 만료됨
        reservationTriggerListener.onFxRateUpdated(event("USD", "KRW", "1280", "0.01"));

        verify(reservationExecutionService, never()).preempt(anyLong());
    }

    @Test
    @DisplayName("USD/KRW 외 통화쌍 이벤트는 무시한다")
    void nonUsdKrw_ignored() {
        reservationTriggerListener.onFxRateUpdated(event("EUR", "KRW", "1400", "0.01"));

        verify(reservationRepository, never()).findByStatusAndAction(any(), any());
    }

    @Test
    @DisplayName("후보 조회가 실패해도 리스너 밖으로 예외를 전파하지 않는다")
    void repositoryFailure_doesNotPropagate() {
        when(reservationRepository.findByStatusAndAction(ReservationStatus.ACTIVE, ReservationAction.EXCHANGE))
                .thenThrow(new RuntimeException("DB 조회 실패"));

        // 예외가 리스너 밖으로 전파되지 않아야 한다(정상 반환).
        reservationTriggerListener.onFxRateUpdated(event("USD", "KRW", "1280", "0.01"));

        verify(reservationExecutionService, never()).preempt(anyLong());
    }
}
