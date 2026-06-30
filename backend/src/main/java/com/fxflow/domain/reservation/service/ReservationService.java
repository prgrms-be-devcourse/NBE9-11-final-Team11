package com.fxflow.domain.reservation.service;

import com.fxflow.domain.reservation.dto.request.ReservationCreateRequest;
import com.fxflow.domain.reservation.dto.response.ReservationPageResponse;
import com.fxflow.domain.reservation.dto.response.ReservationResponse;
import com.fxflow.domain.reservation.entity.Reservation;
import com.fxflow.domain.reservation.enums.ReservationAction;
import com.fxflow.domain.reservation.enums.ReservationStatus;
import com.fxflow.domain.reservation.exception.ReservationErrorCode;
import com.fxflow.domain.reservation.repository.ReservationRepository;
import com.fxflow.global.exception.BusinessException;
import com.fxflow.global.util.KstClock;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationService {

    private final ReservationRepository reservationRepository;

    ///  public 메서드
    /** 예약 생성 (RSV-01) — 멱등 재전송 처리 후, 만료·중복 검증과 통화 정규화를 거쳐 동작별 팩토리로 생성한다. */
    @Transactional
    public ReservationResponse create(Long userId, ReservationCreateRequest request, String idempotencyKey) {
        String fromCurrency = request.fromCurrency().toUpperCase(Locale.ROOT);
        String toCurrency = request.toCurrency().toUpperCase(Locale.ROOT);

        // 멱등: 같은 키로 이미 만든 예약이 있으면 새로 만들지 않고 같은 결과를 돌려준다(송금 도메인과 동일 정책).
        Reservation existing = reservationRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existing != null) {
            return resolveExisting(userId, request, fromCurrency, toCurrency, existing);
        }

        validateNotExpired(request.expiresAt());
        validateNoDuplicate(userId, request, fromCurrency, toCurrency);

        Reservation reservation = toEntity(userId, request, fromCurrency, toCurrency, idempotencyKey);
        return ReservationResponse.from(reservationRepository.save(reservation));
    }

    /** 사용자 예약 목록 조회 (RSV-07) — 최신순 페이지. */
    public ReservationPageResponse getReservations(Long userId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Reservation> reservations = reservationRepository.findByUserId(userId, pageRequest);
        List<ReservationResponse> data = reservations.map(ReservationResponse::from).getContent();
        return ReservationPageResponse.from(reservations, data);
    }

    /** 예약 단건 조회 (RSV-07) — 소유자 본인 예약만. */
    public ReservationResponse getReservation(Long userId, Long reservationId) {
        return ReservationResponse.from(findOwned(userId, reservationId));
    }

    /** 예약 취소 (RSV-08) — ACTIVE 예약만 CANCELED 로 전이(소프트 취소). */
    @Transactional
    public ReservationResponse cancel(Long userId, Long reservationId) {
        Reservation reservation = findOwned(userId, reservationId);
        reservation.cancel();
        return ReservationResponse.from(reservation);
    }

    /**
     * 기한 만료 예약 전이 — 스케줄러가 주기적으로 호출
     * 기한이 지난 ACTIVE 예약을 한 번에 추려 EXPIRED 로 전이하고 전이 건수를 반환한다.
     * 조회가 ACTIVE 만 반환하므로 모든 후보가 만료 대상이며, 트리거 체결은 미만료 ACTIVE 만 대상이라 만료 스캔과 대상이 겹치지 않는다.
     */
    @Transactional
    public int expireOverdueReservations(LocalDateTime now) {
        List<Reservation> overdue =
                reservationRepository.findByStatusAndExpiresAtLessThanEqual(ReservationStatus.ACTIVE, now);
        for (Reservation reservation : overdue) {
            reservation.expire();
        }
        return overdue.size();
    }


    ///  private 메서드
    /** 소유자 본인 예약 조회, 없으면 404. */
    private Reservation findOwned(Long userId, Long reservationId) {
        return reservationRepository.findByIdAndUserId(reservationId, userId)
                .orElseThrow(() -> new BusinessException(ReservationErrorCode.RESERVATION_NOT_FOUND));
    }

    /** 멱등 키가 이미 쓰인 경우: 같은 사용자·같은 요청이면 기존 결과를 반환, 아니면 충돌로 본다. */
    private ReservationResponse resolveExisting(Long userId, ReservationCreateRequest request,
                                               String fromCurrency, String toCurrency, Reservation existing) {
        if (!existing.getUserId().equals(userId)) {
            throw new BusinessException(ReservationErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        }
        if (!isSameRequest(existing, request, fromCurrency, toCurrency)) {
            throw new BusinessException(ReservationErrorCode.INVALID_IDEMPOTENCY_REQUEST);
        }
        return ReservationResponse.from(existing);
    }

    /** 같은 멱등 키로 다른 내용의 예약을 만들려는지 비교(통화는 정규화된 값으로 비교). */
    private boolean isSameRequest(Reservation existing, ReservationCreateRequest request,
                                 String fromCurrency, String toCurrency) {
        return existing.getAction() == request.action()
                && existing.getFromCurrency().equals(fromCurrency)
                && existing.getToCurrency().equals(toCurrency)
                && existing.getAmount().compareTo(request.amount()) == 0
                && existing.getTargetRate().compareTo(request.targetRate()) == 0
                && Objects.equals(existing.getExpiresAt(), request.expiresAt())
                && Objects.equals(existing.getRecipientId(), request.recipientId());
    }

    /**
     * 만료 시각이 과거면 차단. API 경로에선 DTO의 @Future 가 먼저 걸러낸 후(빠른 실패),
     * 이 검증은 @Valid 를 거치지 않는 호출에 대한 백스톱(엔티티는 현재 시각을 비교하지 않음).
     */
    private void validateNotExpired(LocalDateTime expiresAt) {
        // null = 무기한(만료 없음) 허용. 값이 있을 때만 과거 시각을 차단한다.
        if (expiresAt != null && !expiresAt.isAfter(KstClock.now())) {
            throw new BusinessException(ReservationErrorCode.EXPIRES_AT_IN_PAST);
        }
    }

    /**
     * 진행 중(ACTIVE) 동일 조건 예약 차단 — 환전은 통화쌍, 송금은 수취인까지 동일 기준.
     * 주의(MVP 한계): check-then-act 라 서로 다른 멱등 키의 동시 요청에는 레이스가 남는다.
     * DB 레벨 최종 방어(status='ACTIVE' 부분 유니크 인덱스)는 마이그레이션 도구 도입 후 추가 예정.
     */
    private void validateNoDuplicate(Long userId, ReservationCreateRequest request,
                                     String fromCurrency, String toCurrency) {
        boolean duplicated = reservationRepository.existsActiveDuplicate(
                userId, request.action(), fromCurrency, toCurrency,
                ReservationStatus.ACTIVE, request.recipientId());
        if (duplicated) {
            throw new BusinessException(ReservationErrorCode.DUPLICATE_RESERVATION);
        }
    }

    /** 동작에 맞는 정적 팩토리로 예약 엔티티 생성(조건부 필수값 검증은 엔티티가 수행). */
    private Reservation toEntity(Long userId, ReservationCreateRequest request,
                                 String fromCurrency, String toCurrency, String idempotencyKey) {
        if (request.action() == ReservationAction.REMITTANCE) {
            return Reservation.createRemittance(userId, fromCurrency, toCurrency,
                    request.amount(), request.targetRate(), request.expiresAt(),
                    request.recipientId(), request.remittanceReason(), request.remittanceReasonDetail(),
                    idempotencyKey);
        }
        return Reservation.createExchange(userId, fromCurrency, toCurrency,
                request.amount(), request.targetRate(), request.expiresAt(), idempotencyKey);
    }
}
