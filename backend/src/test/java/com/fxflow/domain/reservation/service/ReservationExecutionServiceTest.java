package com.fxflow.domain.reservation.service;

import com.fxflow.domain.reservation.entity.Reservation;
import com.fxflow.domain.reservation.enums.ReservationStatus;
import com.fxflow.domain.reservation.repository.ReservationRepository;
import com.fxflow.global.exception.BusinessException;
import com.fxflow.global.exception.ErrorCode;
import com.fxflow.global.exchange.ExchangeExecutionCommand;
import com.fxflow.global.exchange.ExchangeExecutor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationExecutionServiceTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private ExchangeExecutor exchangeExecutor;

    @InjectMocks
    private ReservationExecutionService reservationExecutionService;

    private static final BigDecimal AMOUNT = new BigDecimal("1000000");
    private static final BigDecimal TARGET = new BigDecimal("1300.00000000");
    private static final LocalDateTime FUTURE = LocalDateTime.now().plusDays(7);

    private Reservation activeExchange() {
        return Reservation.createExchange(1L, "KRW", "USD", AMOUNT, TARGET, FUTURE, "key-1");
    }

    private Reservation triggeredExchange() {
        Reservation reservation = activeExchange();
        reservation.markTriggered();
        return reservation;
    }

    @Test
    @DisplayName("선점 — ACTIVE 예약을 TRIGGERED 로 전이하고 true 반환")
    void preempt_active_marksTriggered() {
        Reservation reservation = activeExchange();
        when(reservationRepository.findById(10L)).thenReturn(Optional.of(reservation));

        boolean result = reservationExecutionService.preempt(10L);

        assertThat(result).isTrue();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.TRIGGERED);
    }

    @Test
    @DisplayName("선점 — 이미 처리된(ACTIVE 아님) 예약이면 false")
    void preempt_nonActive_returnsFalse() {
        Reservation reservation = activeExchange();
        reservation.cancel();
        when(reservationRepository.findById(10L)).thenReturn(Optional.of(reservation));

        boolean result = reservationExecutionService.preempt(10L);

        assertThat(result).isFalse();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELED);
    }

    @Test
    @DisplayName("체결 — 성공 시 COMPLETED 와 결과 거래 ID 기록")
    void executeTriggered_success() {
        Reservation reservation = triggeredExchange();
        when(reservationRepository.findById(10L)).thenReturn(Optional.of(reservation));
        when(exchangeExecutor.execute(any(ExchangeExecutionCommand.class))).thenReturn(777L);

        reservationExecutionService.executeTriggered(10L);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.COMPLETED);
        assertThat(reservation.getResultExchangeTransactionId()).isEqualTo(777L);
    }

    @Test
    @DisplayName("체결 — 비즈니스 예외(잔액 부족 등)면 FAILED 로 기록")
    void executeTriggered_businessException_fails() {
        Reservation reservation = triggeredExchange();
        when(reservationRepository.findById(10L)).thenReturn(Optional.of(reservation));
        when(exchangeExecutor.execute(any(ExchangeExecutionCommand.class)))
                .thenThrow(new BusinessException(testError("잔액이 부족합니다.")));

        reservationExecutionService.executeTriggered(10L);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.FAILED);
        assertThat(reservation.getFailureReason()).isEqualTo("잔액이 부족합니다.");
    }

    @Test
    @DisplayName("체결 — TRIGGERED 가 아니면 아무것도 하지 않음")
    void executeTriggered_notTriggered_skips() {
        Reservation reservation = activeExchange();
        when(reservationRepository.findById(10L)).thenReturn(Optional.of(reservation));

        reservationExecutionService.executeTriggered(10L);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.ACTIVE);
        verify(exchangeExecutor, never()).execute(any());
    }

    private ErrorCode testError(String message) {
        return new ErrorCode() {
            @Override
            public HttpStatus getStatus() {
                return HttpStatus.BAD_REQUEST;
            }

            @Override
            public String getCode() {
                return "TEST-001";
            }

            @Override
            public String getMessage() {
                return message;
            }
        };
    }
}
