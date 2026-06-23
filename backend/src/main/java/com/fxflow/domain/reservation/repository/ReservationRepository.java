package com.fxflow.domain.reservation.repository;

import com.fxflow.domain.reservation.entity.Reservation;
import com.fxflow.domain.reservation.enums.ReservationAction;
import com.fxflow.domain.reservation.enums.ReservationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    // 사용자 예약 목록 (RSV-07) — 정렬은 Pageable 로 전달
    Page<Reservation> findByUserId(Long userId, Pageable pageable);

    // 소유권 확인 단건 조회 (조회/취소 시)
    Optional<Reservation> findByIdAndUserId(Long id, Long userId);

    // 상태별 조회 (예: ACTIVE — 만료 스캔)
    List<Reservation> findByStatus(ReservationStatus status);

    // 상태+동작별 조회 (체결 트리거 — ACTIVE·EXCHANGE 후보 추림)
    List<Reservation> findByStatusAndAction(ReservationStatus status, ReservationAction action);

    // 생성 멱등 — 같은 키 재전송 차단 (가독성과 의도를 위해 Query로 변경)
    Optional<Reservation> findByIdempotencyKey(String idempotencyKey);

    /**
     * 진행 중(ACTIVE) 동일 조건 예약 존재 여부(중복예약 차단).
     * 환전은 recipientId=null 로 호출(통화쌍 기준), 송금은 수취인까지 비교한다.
     */
    @Query("""
            SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END
            FROM Reservation r
            WHERE r.userId = :userId
              AND r.action = :action
              AND r.fromCurrency = :fromCurrency
              AND r.toCurrency = :toCurrency
              AND r.status = :status
              AND (:recipientId IS NULL OR r.recipientId = :recipientId)
            """)
    boolean existsActiveDuplicate(
            @Param("userId") Long userId,
            @Param("action") ReservationAction action,
            @Param("fromCurrency") String fromCurrency,
            @Param("toCurrency") String toCurrency,
            @Param("status") ReservationStatus status,
            @Param("recipientId") Long recipientId
    );
}
