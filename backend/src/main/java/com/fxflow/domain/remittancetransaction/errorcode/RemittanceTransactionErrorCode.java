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
    REMITTANCE_EXCHANGE_RATE_NOT_FOUND(HttpStatus.SERVICE_UNAVAILABLE, "REMITTANCE_EXCHANGE_RATE_NOT_FOUND", "환율 정보를 찾을 수 없습니다."),

    // 송금 주문
    INVALID_REMITTANCE_REASON(HttpStatus.BAD_REQUEST, "INVALID_REMITTANCE_REASON", "송금 사유가 올바르지 않습니다."),
    REMITTANCE_TRANSACTION_NOT_FOUND(HttpStatus.NOT_FOUND, "REMITTANCE_TRANSACTION_NOT_FOUND", "송금 거래를 찾을 수 없습니다."),
    INVALID_REMITTANCE_TRANSACTION_STATUS(HttpStatus.CONFLICT, "INVALID_REMITTANCE_TRANSACTION_STATUS", "현재 상태에서는 입금 확인 처리를 할 수 없습니다."),

    // 가상계좌
    VIRTUAL_ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "VIRTUAL_ACCOUNT_NOT_FOUND", "가상계좌를 찾을 수 없습니다."),
    INVALID_VIRTUAL_ACCOUNT_STATUS(HttpStatus.CONFLICT, "INVALID_VIRTUAL_ACCOUNT_STATUS", "현재 가상계좌 상태에서는 입금 확인 처리를 할 수 없습니다."),
    VIRTUAL_ACCOUNT_EXPIRED(HttpStatus.CONFLICT, "VIRTUAL_ACCOUNT_EXPIRED", "가상계좌 입금 기한이 만료되었습니다."),

    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}