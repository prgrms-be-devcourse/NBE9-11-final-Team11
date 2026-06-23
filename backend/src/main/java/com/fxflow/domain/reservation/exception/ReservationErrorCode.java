package com.fxflow.domain.reservation.exception;

import com.fxflow.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ReservationErrorCode implements ErrorCode {

    INVALID_RESERVATION_REQUEST(HttpStatus.BAD_REQUEST, "R-001", "예약 요청 값이 올바르지 않습니다."),
    RECIPIENT_REQUIRED(HttpStatus.BAD_REQUEST, "R-002", "예약 송금은 수취인이 필요합니다."),
    INVALID_STATUS_TRANSITION(HttpStatus.CONFLICT, "R-003", "현재 예약 상태에서 허용되지 않는 작업입니다."),
    RESERVATION_ACTION_MISMATCH(HttpStatus.CONFLICT, "R-004", "예약 동작 유형과 일치하지 않는 체결 요청입니다."),
    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "R-005", "예약을 찾을 수 없습니다."),
    EXPIRES_AT_IN_PAST(HttpStatus.BAD_REQUEST, "R-006", "만료 시각은 현재 이후여야 합니다."),
    DUPLICATE_RESERVATION(HttpStatus.CONFLICT, "R-007", "이미 동일한 조건의 진행 중인 예약이 있습니다."),
    IDEMPOTENCY_KEY_CONFLICT(HttpStatus.CONFLICT, "R-008", "이미 사용된 멱등 키입니다."),
    INVALID_IDEMPOTENCY_REQUEST(HttpStatus.CONFLICT, "R-009", "같은 멱등 키로 다른 내용의 예약을 처리할 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
