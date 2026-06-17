package com.fxflow.domain.fxrate.exception;

import com.fxflow.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum FxRateErrorCode implements ErrorCode {

    FX_RATE_FETCH_FAILED(HttpStatus.BAD_GATEWAY, "F-001", "환율 정보를 가져오지 못했습니다."),
    FX_RATE_NOT_FOUND(HttpStatus.NOT_FOUND, "F-002", "환율 정보를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
