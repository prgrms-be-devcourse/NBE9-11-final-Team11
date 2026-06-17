package com.fxflow.domain.userlimitusage.errorcode;

import com.fxflow.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum UserLimitUsageErrorCode implements ErrorCode {

    DAILY_USAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "DAILY_USAGE_NOT_FOUND", "일일 사용량 정보를 찾을 수 없습니다."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
