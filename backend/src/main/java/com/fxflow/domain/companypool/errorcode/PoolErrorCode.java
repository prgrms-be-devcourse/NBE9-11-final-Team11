package com.fxflow.domain.companypool.errorcode;

import com.fxflow.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PoolErrorCode implements ErrorCode {

    POOL_NOT_FOUND(HttpStatus.NOT_FOUND, "POOL_NOT_FOUND", "통화 풀을 찾을 수 없습니다."),
    POOL_INSUFFICIENT_BALANCE(HttpStatus.CONFLICT, "POOL_INSUFFICIENT_BALANCE", "풀 잔액이 부족하여 처리할 수 없습니다."),
    REBALANCE_IN_PROGRESS(HttpStatus.CONFLICT, "REBALANCE_IN_PROGRESS", "리밸런싱이 이미 진행 중입니다."),
    BOTH_BELOW_FLOOR(HttpStatus.CONFLICT, "BOTH_BELOW_FLOOR", "양 통화 모두 부족하여 환전으로 조정 불가합니다."),
    RATE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "RATE_UNAVAILABLE", "환율 조회 실패로 실행할 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
