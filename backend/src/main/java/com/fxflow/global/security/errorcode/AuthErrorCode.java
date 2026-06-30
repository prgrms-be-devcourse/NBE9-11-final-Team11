package com.fxflow.global.security.errorcode;

import com.fxflow.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {

    UNAUTHORIZED(
            HttpStatus.UNAUTHORIZED,
            "UNAUTHORIZED",
            "인증되지 않은 사용자입니다."
    ),
    ACCESS_DENIED(
            HttpStatus.FORBIDDEN,
            "ACCESS_DENIED",
            "접근 권한이 없습니다."
    ),
    REFRESH_TOKEN_INVALID(
            HttpStatus.UNAUTHORIZED,
            "REFRESH_TOKEN_INVALID",
            "유효하지 않은 Refresh Token입니다."
    ),
    REFRESH_TOKEN_EXPIRED(
            HttpStatus.UNAUTHORIZED,
            "REFRESH_TOKEN_EXPIRED",
            "Refresh Token이 만료되었습니다. 다시 로그인해주세요."
    );


    private final HttpStatus status;
    private final String code;
    private final String message;
}