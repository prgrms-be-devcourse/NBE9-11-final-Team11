package com.fxflow.domain.reservation.controller;

import com.fxflow.domain.reservation.dto.request.ReservationCreateRequest;
import com.fxflow.domain.reservation.dto.response.ReservationPageResponse;
import com.fxflow.domain.reservation.dto.response.ReservationResponse;
import com.fxflow.domain.reservation.service.ReservationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/reservations")
@Validated
public class ReservationController {

    private final ReservationService reservationService;

    /** 예약 생성 (RSV-01) — 멱등 키는 헤더로 받는다. */
    @PostMapping
    public ResponseEntity<ReservationResponse> create(
            @AuthenticationPrincipal Long userId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ReservationCreateRequest request
    ) {
        ReservationResponse response = reservationService.create(userId, request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** 사용자 예약 목록 조회 (RSV-07) — 최신순 페이지. */
    @GetMapping
    public ResponseEntity<ReservationPageResponse> getReservations(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ResponseEntity.ok(reservationService.getReservations(userId, page, size));
    }

    /** 예약 단건 조회 (RSV-07). */
    @GetMapping("/{reservationId}")
    public ResponseEntity<ReservationResponse> getReservation(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long reservationId
    ) {
        return ResponseEntity.ok(reservationService.getReservation(userId, reservationId));
    }

    /** 예약 취소 (RSV-08) — 체결 전(ACTIVE) 예약만 취소 가능. */
    @PatchMapping("/{reservationId}/cancel")
    public ResponseEntity<ReservationResponse> cancel(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long reservationId
    ) {
        return ResponseEntity.ok(reservationService.cancel(userId, reservationId));
    }
}
