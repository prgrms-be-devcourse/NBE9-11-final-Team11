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
    RESERVATION_ACTION_MISMATCH(HttpStatus.CONFLICT, "R-004", "예약 동작 유형과 일치하지 않는 체결 요청입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
