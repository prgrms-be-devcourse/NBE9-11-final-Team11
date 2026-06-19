package com.fxflow.domain.fxrate.dto.response;

import com.fxflow.global.fx.FxRateSnapshot;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    // 저장 정밀도(8자리)와 동일하게 노출 — 표시용 반올림(예: 2자리)은 프론트 영역
    private static final int RATE_SCALE = 8;

    public static FxRateResponse from(FxRateSnapshot snapshot) {
        return new FxRateResponse(
                snapshot.baseCurrency(),
                snapshot.quoteCurrency(),
                normalize(snapshot.midRate()),
                normalize(snapshot.buyRate()),
                normalize(snapshot.sellRate()),
                snapshot.fetchedAt()
        );
    }

    // 파생값(매수/매도)의 BigDecimal 곱셈 잔재를 제거하고 mid와 자리수를 맞추기 위함
    private static BigDecimal normalize(BigDecimal rate) {
        return rate.setScale(RATE_SCALE, RoundingMode.HALF_UP);
    }
}
