package com.fxflow.domain.reservation.repository;

import com.fxflow.domain.reservation.entity.Reservation;
import com.fxflow.domain.reservation.enums.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    // 사용자 예약 목록 (RSV-07)
    List<Reservation> findByUserIdOrderByCreatedAtDesc(Long userId);

    // 소유권 확인 단건 조회 (조회/취소 시)
    Optional<Reservation> findByIdAndUserId(Long id, Long userId);

    // 상태별 조회 (예: ACTIVE — 도달 판정·만료 스캔)
    List<Reservation> findByStatus(ReservationStatus status);
}
