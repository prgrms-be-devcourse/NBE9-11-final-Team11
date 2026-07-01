package com.fxflow.global.fx;

import com.fxflow.global.exception.BusinessException;

import java.util.Optional;

/**
 * 다른 도메인(환전·송금·알림 등)이 최신 환율을 조회하는 공통 포트.
 * fxrate 도메인이 구현하며, 소비 도메인은 fxrate 내부가 아닌 이 인터페이스에만 의존한다.
 * B(환전)·C(송금)는 외부 환율 API를 직접 호출하지 말고 반드시 이 포트를 주입받아 사용한다.
 */
public interface ExchangeRateProvider {

    // 통화쌍의 가장 최신 환율 스냅샷 조회 (저장된 환율이 없으면 empty). 신선도 검증 없음 — 단순 표시(조회) 용도 전용.
    Optional<FxRateSnapshot> getLatestRate(String baseCurrency, String quoteCurrency);

    /**
     * 최신 환율 스냅샷 조회 + 신선도 검증. 환전·송금·리밸런싱 등 자금이 이동하는 거래에서 사용한다.
     * @throws BusinessException 환율 수집이 장시간 실패해 데이터 신선도가 보장되지 않을 때 (FX_RATE_STALE)
     */
    Optional<FxRateSnapshot> getLatestRateOrThrowIfStale(String baseCurrency, String quoteCurrency);
}
