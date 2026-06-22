package com.fxflow.domain.reservation.entity;

import com.fxflow.domain.reservation.enums.ReservationAction;
import com.fxflow.domain.reservation.enums.ReservationStatus;
import com.fxflow.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservationTest {

    private static final BigDecimal AMOUNT = new BigDecimal("1000000");          // 100만 (KRW)
    private static final BigDecimal TARGET = new BigDecimal("1300.00000000");    // 목표 환율
    private static final LocalDateTime EXPIRES = LocalDateTime.of(2026, 12, 31, 0, 0);

    @Test
    @DisplayName("예약 환전 생성 시 ACTIVE 상태로 만들어진다")
    void createExchange_startsActive() {
        Reservation r = Reservation.createExchange(1L, "KRW", "USD", AMOUNT, TARGET, EXPIRES, "key-1");

        assertThat(r.getStatus()).isEqualTo(ReservationStatus.ACTIVE);
        assertThat(r.getAction()).isEqualTo(ReservationAction.EXCHANGE);
        assertThat(r.getRecipientId()).isNull();
    }

    @Test
    @DisplayName("예약 송금은 recipientId가 있어야 생성된다")
    void createRemittance_requiresRecipient() {
        assertThatThrownBy(() ->
                Reservation.createRemittance(1L, "KRW", "USD", AMOUNT, TARGET, EXPIRES, null, "key-2"))
                .isInstanceOf(BusinessException.class);

        Reservation r = Reservation.createRemittance(1L, "KRW", "USD", AMOUNT, TARGET, EXPIRES, 99L, "key-3");
        assertThat(r.getRecipientId()).isEqualTo(99L);
        assertThat(r.getAction()).isEqualTo(ReservationAction.REMITTANCE);
    }

    @Test
    @DisplayName("필수값 누락·잘못된 값이면 생성 거부")
    void create_rejectsInvalid() {
        assertThatThrownBy(() -> Reservation.createExchange(null, "KRW", "USD", AMOUNT, TARGET, EXPIRES, "k"))
                .isInstanceOf(BusinessException.class);   // userId null
        assertThatThrownBy(() -> Reservation.createExchange(1L, " ", "USD", AMOUNT, TARGET, EXPIRES, "k"))
                .isInstanceOf(BusinessException.class);   // blank currency
        assertThatThrownBy(() -> Reservation.createExchange(1L, "KRW", "KRW", AMOUNT, TARGET, EXPIRES, "k"))
                .isInstanceOf(BusinessException.class);   // same currency
        assertThatThrownBy(() -> Reservation.createExchange(1L, "KRW", "USD", BigDecimal.ZERO, TARGET, EXPIRES, "k"))
                .isInstanceOf(BusinessException.class);   // non-positive amount
        assertThatThrownBy(() -> Reservation.createExchange(1L, "KRW", "USD", AMOUNT, BigDecimal.ZERO, EXPIRES, "k"))
                .isInstanceOf(BusinessException.class);   // non-positive targetRate
        assertThatThrownBy(() -> Reservation.createExchange(1L, "KRW", "USD", AMOUNT, TARGET, null, "k"))
                .isInstanceOf(BusinessException.class);   // null expiresAt
        assertThatThrownBy(() -> Reservation.createExchange(1L, "KRW", "USD", AMOUNT, TARGET, EXPIRES, " "))
                .isInstanceOf(BusinessException.class);   // blank idempotencyKey
    }

    @Test
    @DisplayName("정상 상태 전이: ACTIVE → TRIGGERED → COMPLETED")
    void transition_triggerThenComplete() {
        Reservation r = Reservation.createExchange(1L, "KRW", "USD", AMOUNT, TARGET, EXPIRES, "key-4");

        r.markTriggered();
        assertThat(r.getStatus()).isEqualTo(ReservationStatus.TRIGGERED);

        r.markCompletedAsExchange(500L);
        assertThat(r.getStatus()).isEqualTo(ReservationStatus.COMPLETED);
        assertThat(r.getResultExchangeTransactionId()).isEqualTo(500L);
    }

    @Test
    @DisplayName("취소는 ACTIVE 에서만 가능 — 체결 시작 후 취소 불가")
    void cancel_onlyFromActive() {
        Reservation r = Reservation.createExchange(1L, "KRW", "USD", AMOUNT, TARGET, EXPIRES, "key-5");
        r.markTriggered();

        assertThatThrownBy(r::cancel).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("체결 실패 시 FAILED + 사유 기록")
    void fail_recordsReason() {
        Reservation r = Reservation.createExchange(1L, "KRW", "USD", AMOUNT, TARGET, EXPIRES, "key-6");
        r.markTriggered();

        r.fail("잔액 부족");
        assertThat(r.getStatus()).isEqualTo(ReservationStatus.FAILED);
        assertThat(r.getFailureReason()).isEqualTo("잔액 부족");
    }

    @Test
    @DisplayName("이중 체결 방지: TRIGGERED 에서 다시 markTriggered 불가")
    void markTriggered_rejectsDoubleTrigger() {
        Reservation r = Reservation.createExchange(1L, "KRW", "USD", AMOUNT, TARGET, EXPIRES, "key-7");
        r.markTriggered();

        assertThatThrownBy(r::markTriggered).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("동작 불일치 체결 거부: EXCHANGE 예약을 remittance 결과로 완료 불가")
    void markCompleted_rejectsActionMismatch() {
        Reservation r = Reservation.createExchange(1L, "KRW", "USD", AMOUNT, TARGET, EXPIRES, "key-8");
        r.markTriggered();

        assertThatThrownBy(() -> r.markCompletedAsRemittance(700L))
                .isInstanceOf(BusinessException.class);
    }
}
