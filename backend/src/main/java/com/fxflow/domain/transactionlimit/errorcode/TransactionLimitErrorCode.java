package com.fxflow.domain.transactionlimit.errorcode;

import com.fxflow.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TransactionLimitErrorCode implements ErrorCode {

    LIMIT_POLICY_NOT_FOUND(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "LIMIT_POLICY_NOT_FOUND",
            "한도 정책을 찾을 수 없습니다."
    ),
    PER_TRANSACTION_LIMIT_EXCEEDED(
            HttpStatus.CONFLICT,
            "PER_TRANSACTION_LIMIT_EXCEEDED",
            "건당 송금 한도를 초과했습니다."
    ),
    ANNUAL_LIMIT_EXCEEDED(
            HttpStatus.CONFLICT,
            "ANNUAL_LIMIT_EXCEEDED",
            "연간 송금 한도를 초과했습니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}