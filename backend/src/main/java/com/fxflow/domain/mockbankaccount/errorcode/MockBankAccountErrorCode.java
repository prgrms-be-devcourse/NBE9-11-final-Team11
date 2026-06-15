package com.fxflow.domain.mockbankaccount.errorcode;

import com.fxflow.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum MockBankAccountErrorCode implements ErrorCode {

    // 연결 관련
    MOCK_ACCOUNT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "MOCK_ACCOUNT_NOT_FOUND",
            "연결된 모의계좌가 없습니다."
    ),
    MOCK_ACCOUNT_ALREADY_LINKED(
            HttpStatus.CONFLICT,
            "MOCK_ACCOUNT_ALREADY_LINKED",
            "이미 연결된 모의계좌가 있습니다."
    ),
    MOCK_ACCOUNT_NUMBER_DUPLICATED(
            HttpStatus.CONFLICT,
            "MOCK_ACCOUNT_NUMBER_DUPLICATED",
            "이미 사용 중인 계좌번호입니다."
    ),
    //잘못된 입금
    MOCK_ACCOUNT_INVALID_AMOUNT(
            HttpStatus.BAD_REQUEST,
            "MOCK_ACCOUNT_INVALID_AMOUNT",
            "금액은 0보다 커야 합니다."
    ),

    // 잔액 관련
    MOCK_ACCOUNT_INSUFFICIENT_BALANCE(
            HttpStatus.CONFLICT,
            "MOCK_ACCOUNT_INSUFFICIENT_BALANCE",
            "모의계좌 잔액이 부족합니다."
    ),

    // 형식 관련
    MOCK_ACCOUNT_INVALID_FORMAT(
            HttpStatus.BAD_REQUEST,
            "MOCK_ACCOUNT_INVALID_FORMAT",
            "계좌번호 형식이 올바르지 않습니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}