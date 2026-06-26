package com.fxflow.domain.wallet.errorcode;

import com.fxflow.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum LotErrorCode implements ErrorCode {

    INSUFFICIENT_LOT_BALANCE(HttpStatus.UNPROCESSABLE_CONTENT, "INSUFFICIENT_LOT_BALANCE", "보유 외화 잔액이 부족합니다."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
