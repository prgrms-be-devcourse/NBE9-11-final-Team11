package com.fxflow.domain.fxrate.dto.response;

import com.fxflow.global.fx.FxRateSnapshot;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 최신 환율 조회 응답.
 * mid(기준 환율)와 매수/매도 적용가, 최근 갱신 시각을 함께 노출
 * (spread는 내부 마진이므로 응답에 노출하지 않음)
 */
public record FxRateResponse(
        String baseCurrency,     // 기준 통화
        String quoteCurrency,    // 상대 통화
        BigDecimal midRate,      // 기준 환율 (mid)
        BigDecimal buyRate,      // 고객 매수 적용가
        BigDecimal sellRate,     // 고객 매도 적용가
        LocalDateTime fetchedAt  // 최근 갱신 시각
) {
    public static FxRateResponse from(FxRateSnapshot snapshot) {
        return new FxRateResponse(
                snapshot.baseCurrency(),
                snapshot.quoteCurrency(),
                snapshot.midRate(),
                snapshot.buyRate(),
                snapshot.sellRate(),
                snapshot.fetchedAt()
        );
    }
}
