package com.fxflow.domain.wallet.errorcode;

import com.fxflow.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum P2pTransferErrorCode implements ErrorCode {

    SELF_TRANSFER_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "SELF_TRANSFER_NOT_ALLOWED", "자기 자신한테 송금할 수 없습니다."),
    ;
    private final HttpStatus status;
    private final String code;
    private final String message;
}
