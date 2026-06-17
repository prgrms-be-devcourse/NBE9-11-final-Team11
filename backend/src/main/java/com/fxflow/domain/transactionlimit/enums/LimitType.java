package com.fxflow.domain.transactionlimit.enums;

public enum LimitType {
    // ── 해외송금 한도 ───────────────────────────────────────
    PER_REMITTANCE,         // 건당 송금 한도  (USD $5,000)
    ANNUAL_REMITTANCE,      // 연간 송금 한도  (USD $100,000)

    // ── 모의계좌 입금 한도 ──────────────────────────────────
    PER_DEPOSIT,            // 1회 입금 한도   (KRW 200만/300만)
    DAILY_DEPOSIT,          // 일일 입금 한도  (KRW 200만/300만)

    // ── 모의계좌 출금 한도 ──────────────────────────────────
    PER_WITHDRAWAL,         // 1회 출금 한도   (KRW 200만/300만)
    DAILY_WITHDRAWAL,        // 일일 출금 한도  (KRW 200만/300만)
    // ── 모의계좌 출금 한도 ──────────────────────────────────
    PER_EXCHANGE,           // 건당 환전 한도  (KRW 200만)
    DAILY_EXCHANGE,         // 일일 환전 한도  (KRW 200만)
    ANNUAL_EXCHANGE         // 연간 환전 한도  (USD $100,000)
}