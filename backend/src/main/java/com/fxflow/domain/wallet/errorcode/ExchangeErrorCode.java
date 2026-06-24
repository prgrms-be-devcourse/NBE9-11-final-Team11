package com.fxflow.domain.wallet.errorcode;

import com.fxflow.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ExchangeErrorCode implements ErrorCode {

    FEE_RATE_NOT_FOUND(HttpStatus.NOT_FOUND, "FEE_RATE_NOT_FOUND", "요청한 통화쌍의 수수료율 정보를 찾을 수 없습니다."),
    QUOTE_NOT_FOUND(HttpStatus.NOT_FOUND, "QUOTE_NOT_FOUND", "환전 견적 정보를 찾을 수 없습니다."),
    MINIMUM_AMOUNT_NOT_MET(HttpStatus.BAD_REQUEST, "MINIMUM_AMOUNT_NOT_MET", "환전 최소 금액 이상이어야 합니다."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
