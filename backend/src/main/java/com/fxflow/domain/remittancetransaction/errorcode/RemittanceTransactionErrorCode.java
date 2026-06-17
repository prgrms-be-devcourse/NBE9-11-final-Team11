package com.fxflow.domain.remittancetransaction.errorcode;

import com.fxflow.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum RemittanceTransactionErrorCode implements ErrorCode {

    // 송금 견적
    QUOTE_NOT_FOUND(HttpStatus.NOT_FOUND, "REMITTANCE_QUOTE_NOT_FOUND", "송금 견적을 찾을 수 없습니다."),
    QUOTE_EXPIRED(HttpStatus.CONFLICT, "REMITTANCE_QUOTE_EXPIRED", "송금 견적이 만료되었습니다."),

    // 송금 주문
    INVALID_REMITTANCE_REASON(HttpStatus.BAD_REQUEST, "INVALID_REMITTANCE_REASON", "송금 사유가 올바르지 않습니다."),

    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}