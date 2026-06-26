package com.fxflow.domain.wallet.errorcode;

import com.fxflow.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum WalletErrorCode implements ErrorCode {

    // 공통/조회
    WALLET_NOT_FOUND(HttpStatus.NOT_FOUND, "WALLET_NOT_FOUND", "지갑을 찾을 수 없습니다."),
    INVALID_CURRENCY(HttpStatus.BAD_REQUEST, "INVALID_CURRENCY", "지원하지 않는 통화입니다."),
    INVALID_AMOUNT(HttpStatus.BAD_REQUEST, "INVALID_AMOUNT", "금액은 0보다 커야 합니다."),
    INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE", "조회 기간이 올바르지 않습니다."),
    UNSUPPORTED_CURRENCY_FOR_PROFIT(HttpStatus.BAD_REQUEST, "UNSUPPORTED_CURRENCY_FOR_PROFIT", "손익이 지원되지 않는 통화입니다."),

    // 충전/인출
    BANK_ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "BANK_ACCOUNT_NOT_FOUND", "모의계좌를 찾을 수 없습니다."),
    BANK_ACCOUNT_NOT_LINKED(HttpStatus.CONFLICT, "BANK_ACCOUNT_NOT_LINKED", "연동된 계좌가 없습니다."),
    INSUFFICIENT_BALANCE(HttpStatus.CONFLICT, "INSUFFICIENT_BALANCE", "잔액이 부족합니다."),

    // 환전
    INVALID_CURRENCY_PAIR(HttpStatus.BAD_REQUEST, "INVALID_CURRENCY_PAIR", "출발 통화와 도착 통화가 같을 수 없습니다."),
    EXCHANGE_RATE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "EXCHANGE_RATE_UNAVAILABLE", "환율 정보를 가져올 수 없습니다."),
    QUOTE_NOT_FOUND(HttpStatus.NOT_FOUND, "QUOTE_NOT_FOUND", "견적 정보를 찾을 수 없습니다."),
    QUOTE_EXPIRED(HttpStatus.CONFLICT, "QUOTE_EXPIRED", "견적이 만료되었습니다."),
    QUOTE_ALREADY_USED(HttpStatus.CONFLICT, "QUOTE_ALREADY_USED", "이미 사용된 견적입니다."),
    POOL_UPDATE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "POOL_UPDATE_FAILED", "통화 풀 갱신에 실패했습니다."),

    // P2P
    RECIPIENT_NOT_FOUND(HttpStatus.NOT_FOUND, "RECIPIENT_NOT_FOUND", "수취인을 찾을 수 없습니다."),
    SELF_TRANSFER_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "SELF_TRANSFER_NOT_ALLOWED", "본인에게는 송금할 수 없습니다."),

    ;


    private final HttpStatus status;
    private final String code;
    private final String message;
}
