package com.fxflow.domain.reservation.enums;

public enum ReservationStatus {
    ACTIVE,     // 등록·대기 (목표 환율 도달 대기)
    TRIGGERED,  // 목표 도달 — 체결 실행 중
    COMPLETED,  // 체결 완료
    CANCELED,   // 사용자 취소 (체결 전)
    FAILED,     // 체결 실패 (잔액 부족·한도 초과 등)
    EXPIRED     // 만료 (기한 내 미도달)
}
