package com.fxflow.domain.fxrate.exception;

import com.fxflow.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum FxRateErrorCode implements ErrorCode {

    FX_RATE_FETCH_FAILED(HttpStatus.BAD_GATEWAY, "F-001", "환율 정보를 가져오지 못했습니다."),
    FX_RATE_NOT_FOUND(HttpStatus.NOT_FOUND, "F-002", "환율 정보를 찾을 수 없습니다."),
    FX_RATE_INVALID_PERIOD(HttpStatus.BAD_REQUEST, "F-003", "유효하지 않은 조회 기간입니다."),
    FX_RATE_STALE(HttpStatus.SERVICE_UNAVAILABLE, "F-004", "환율 정보가 장시간 갱신되지 않아 현재 거래가 제한됩니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
