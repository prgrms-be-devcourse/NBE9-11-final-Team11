package com.fxflow.global.fx;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 도메인 간 전달용 환율 스냅샷 (특정 시점의 최신 환율).
 * mid + spread를 출처로 보관하고, 매수/매도 적용가는 파생 메서드로 제공
 * Todo. (파생값 반올림은 사용하는 쪽에서 시점·정책에 맞게 처리할 예정)
 */
public record FxRateSnapshot(
        String baseCurrency,     // 기준 통화
        String quoteCurrency,    // 상대 통화
        BigDecimal midRate,      // 기준 환율 (= baseRate)
        BigDecimal spread,       // 비율 스프레드 (예: 0.01 = 1%)
        LocalDateTime fetchedAt  // 수집 시각
) {
    // 고객 매수 적용가 (mid × (1 + spread))
    public BigDecimal buyRate() {
        return midRate.multiply(BigDecimal.ONE.add(spread));
    }

    // 고객 매도 적용가 (mid × (1 − spread))
    public BigDecimal sellRate() {
        return midRate.multiply(BigDecimal.ONE.subtract(spread));
    }
}
