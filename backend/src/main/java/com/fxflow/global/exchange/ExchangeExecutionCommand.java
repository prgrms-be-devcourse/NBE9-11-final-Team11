package com.fxflow.global.exchange;

import java.math.BigDecimal;

/**
 * 즉시 환전 체결 요청 — 다른 도메인(예: 예약 환전 체결)이 wallet 에 환전 실행을 요청할 때 전달.
 * 적용 환율은 호출 시점의 시세로 결정되며, 이 커맨드에는 담지 않음.
 */
public record ExchangeExecutionCommand(
        Long userId,
        String fromCurrency,
        String toCurrency,
        BigDecimal amount
) {
}
