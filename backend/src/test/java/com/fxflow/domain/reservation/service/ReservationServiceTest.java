package com.fxflow.domain.reservation.service;

import com.fxflow.domain.reservation.dto.request.ReservationCreateRequest;
import com.fxflow.domain.reservation.dto.response.ReservationPageResponse;
import com.fxflow.domain.reservation.dto.response.ReservationResponse;
import com.fxflow.domain.reservation.entity.Reservation;
import com.fxflow.domain.reservation.enums.ReservationAction;
import com.fxflow.domain.reservation.enums.ReservationStatus;
import com.fxflow.domain.reservation.repository.ReservationRepository;
import com.fxflow.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private ReservationRepository reservationRepository;

    @InjectMocks
    private ReservationService reservationService;

    private static final BigDecimal AMOUNT = new BigDecimal("1000000");
    private static final BigDecimal TARGET = new BigDecimal("1300.00000000");
    private static final LocalDateTime FUTURE = LocalDateTime.now().plusDays(7);

    private ReservationCreateRequest exchangeRequest(String from, String to) {
        return new ReservationCreateRequest(
                ReservationAction.EXCHANGE, from, to, AMOUNT, TARGET, FUTURE,
                null, null, null);
    }

    @Test
    @DisplayName("예약 환전 생성 — 통화를 대문자로 정규화해 저장한다")
    void create_exchange_normalizesCurrency() {
        when(reservationRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
        when(reservationRepository.existsActiveDuplicate(
                anyLong(), any(), anyString(), anyString(), any(), any())).thenReturn(false);
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> inv.getArgument(0));

        ReservationResponse res = reservationService.create(1L, exchangeRequest("krw", "usd"), "key-1");

        ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);
        verify(reservationRepository).save(captor.capture());
        assertThat(captor.getValue().getFromCurrency()).isEqualTo("KRW");
        assertThat(captor.getValue().getToCurrency()).isEqualTo("USD");
        assertThat(res.status()).isEqualTo(ReservationStatus.ACTIVE);
    }

    @Test
    @DisplayName("예약 생성 — 같은 키·같은 사용자·같은 요청이면 기존 예약을 반환(멱등 재전송)")
    void create_sameKeySameRequest_returnsExisting() {
        Reservation existing =
                Reservation.createExchange(1L, "KRW", "USD", AMOUNT, TARGET, FUTURE, "key-1");
        when(reservationRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));

        ReservationResponse res = reservationService.create(1L, exchangeRequest("KRW", "USD"), "key-1");

        assertThat(res.status()).isEqualTo(ReservationStatus.ACTIVE);
        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("예약 생성 — 같은 키를 다른 사용자가 쓰면 충돌")
    void create_sameKeyDifferentUser_conflict() {
        Reservation existing =
                Reservation.createExchange(2L, "KRW", "USD", AMOUNT, TARGET, FUTURE, "key-1");
        when(reservationRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> reservationService.create(1L, exchangeRequest("KRW", "USD"), "key-1"))
                .isInstanceOf(BusinessException.class);
        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("예약 생성 — 같은 키로 다른 내용을 요청하면 거부")
    void create_sameKeyDifferentRequest_rejected() {
        Reservation existing =
                Reservation.createExchange(1L, "KRW", "USD", AMOUNT, TARGET, FUTURE, "key-1");
        when(reservationRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));
        ReservationCreateRequest different = new ReservationCreateRequest(
                ReservationAction.EXCHANGE, "KRW", "USD", new BigDecimal("2000000"), TARGET, FUTURE,
                null, null, null);

        assertThatThrownBy(() -> reservationService.create(1L, different, "key-1"))
                .isInstanceOf(BusinessException.class);
        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("예약 생성 — 만료 시각이 과거면 거부")
    void create_rejectsPastExpiresAt() {
        ReservationCreateRequest past = new ReservationCreateRequest(
                ReservationAction.EXCHANGE, "KRW", "USD", AMOUNT, TARGET,
                LocalDateTime.now().minusDays(1), null, null, null);
        when(reservationRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reservationService.create(1L, past, "key-1"))
                .isInstanceOf(BusinessException.class);
        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("예약 생성 — 동일 조건 진행 중 예약이 있으면 중복 거부")
    void create_rejectsDuplicateActive() {
        when(reservationRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
        when(reservationRepository.existsActiveDuplicate(
                anyLong(), any(), anyString(), anyString(), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> reservationService.create(1L, exchangeRequest("KRW", "USD"), "key-1"))
                .isInstanceOf(BusinessException.class);
        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("단건 조회 — 본인 예약이 없으면 404")
    void getReservation_notFound() {
        when(reservationRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reservationService.getReservation(1L, 99L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("취소 — ACTIVE 예약을 CANCELED 로 전이")
    void cancel_success() {
        Reservation reservation =
                Reservation.createExchange(1L, "KRW", "USD", AMOUNT, TARGET, FUTURE, "key-1");
        when(reservationRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(reservation));

        ReservationResponse res = reservationService.cancel(1L, 10L);

        assertThat(res.status()).isEqualTo(ReservationStatus.CANCELED);
    }

    @Test
    @DisplayName("목록 조회 — 페이지 메타와 데이터를 매핑한다")
    void getReservations_mapsPage() {
        Reservation reservation =
                Reservation.createExchange(1L, "KRW", "USD", AMOUNT, TARGET, FUTURE, "key-1");
        Page<Reservation> page = new PageImpl<>(List.of(reservation), Pageable.ofSize(20), 1);
        when(reservationRepository.findByUserId(eq(1L), any(Pageable.class))).thenReturn(page);

        ReservationPageResponse res = reservationService.getReservations(1L, 0, 20);

        assertThat(res.data()).hasSize(1);
        assertThat(res.totalElements()).isEqualTo(1);
    }
}
