package com.fxflow.domain.transactionlimit.errorcode;

import com.fxflow.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TransactionLimitErrorCode implements ErrorCode {

    // ── 정책 조회 ──────────────────────────────────────────────────────────
    LIMIT_POLICY_NOT_FOUND(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "LIMIT_POLICY_NOT_FOUND",
            "한도 정책을 찾을 수 없습니다."
    ),

    // ── 해외송금 한도 ───────────────────────────────────────────────────────
    PER_REMITTANCE_LIMIT_EXCEEDED(
            HttpStatus.CONFLICT,
            "PER_REMITTANCE_LIMIT_EXCEEDED",
            "건당 송금 한도를 초과했습니다."         // USD $5,000
    ),
    ANNUAL_REMITTANCE_LIMIT_EXCEEDED(
            HttpStatus.CONFLICT,
            "ANNUAL_REMITTANCE_LIMIT_EXCEEDED",
            "연간 송금 한도를 초과했습니다."         // USD $100,000
    ),

    // ── 모의계좌 입금 한도 ──────────────────────────────────────────────────
    PER_DEPOSIT_LIMIT_EXCEEDED(
            HttpStatus.CONFLICT,
            "PER_DEPOSIT_LIMIT_EXCEEDED",
            "1회 입금 한도를 초과했습니다."          // KRW 200만/300만
    ),
    DAILY_DEPOSIT_LIMIT_EXCEEDED(
            HttpStatus.CONFLICT,
            "DAILY_DEPOSIT_LIMIT_EXCEEDED",
            "일일 입금 한도를 초과했습니다."         // KRW 200만/300만
    ),

    // ── 모의계좌 출금 한도 ──────────────────────────────────────────────────
    PER_WITHDRAWAL_LIMIT_EXCEEDED(
            HttpStatus.CONFLICT,
            "PER_WITHDRAWAL_LIMIT_EXCEEDED",
            "1회 출금 한도를 초과했습니다."          // KRW 200만/300만
    ),
    DAILY_WITHDRAWAL_LIMIT_EXCEEDED(
            HttpStatus.CONFLICT,
            "DAILY_WITHDRAWAL_LIMIT_EXCEEDED",
            "일일 출금 한도를 초과했습니다."         // KRW 200만/300만
    ),

    // ── 월렛 보유 한도 ──────────────────────────────────────────────────────
    WALLET_HOLDING_LIMIT_EXCEEDED(
            HttpStatus.CONFLICT,
            "WALLET_HOLDING_LIMIT_EXCEEDED",
            "월렛 보유 한도를 초과했습니다."         // KRW 200만/300만
    ),
    PER_EXCHANGE_LIMIT_EXCEEDED(
            HttpStatus.CONFLICT,
            "PER_EXCHANGE_LIMIT_EXCEEDED",
            "건당 환전 한도를 초과했습니다."         // KRW 200만
    ),
    DAILY_EXCHANGE_LIMIT_EXCEEDED(
            HttpStatus.CONFLICT,
            "DAILY_EXCHANGE_LIMIT_EXCEEDED",
            "일일 환전 한도를 초과했습니다."         // KRW 200만
    ),
    ANNUAL_EXCHANGE_LIMIT_EXCEEDED(
            HttpStatus.CONFLICT,
            "ANNUAL_EXCHANGE_LIMIT_EXCEEDED",
            "연간 환전 한도를 초과했습니다."         // USD $100,000
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}