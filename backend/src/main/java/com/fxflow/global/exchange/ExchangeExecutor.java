package com.fxflow.global.exchange;

/**
 * 즉시 환전 체결 포트 — 도메인 간 직접 의존을 피하기 위한 추상화.
 * 호출 도메인(reservation 등)은 이 포트에만 의존하고, wallet 도메인이 구현.
 * (fxrate 의 ExchangeRateProvider 포트와 동일한 의존성 역전 패턴)
 */
public interface ExchangeExecutor {

    /**
     * 현재 시세 기준으로 환전을 즉시 체결하고, 생성된 환전 거래의 식별자(PK)를 반환.
     * 잔액 부족·한도 초과 등 비즈니스 사유는 BusinessException 으로 전달됨.
     */
    Long execute(ExchangeExecutionCommand command);
}
