package com.fxflow.domain.fxrate.dto.response;

import com.fxflow.global.fx.FxRateSnapshot;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * 최신 환율 조회 응답.
 * mid(기준 환율)와 매수/매도 적용가, 최근 갱신 시각, 전일(15:30 기준) 대비 변동을 함께 노출
 * (spread는 내부 마진이므로 응답에 노출하지 않음)
 */
public record FxRateResponse(
        String baseCurrency,      // 기준 통화
        String quoteCurrency,     // 상대 통화
        BigDecimal midRate,       // 기준 환율 (mid)
        BigDecimal buyRate,       // 고객 매수 적용가
        BigDecimal sellRate,      // 고객 매도 적용가
        LocalDateTime fetchedAt,  // 최근 갱신 시각
        BigDecimal previousRate,  // 전일 15:30 기준 매매기준율 (기준값 없으면 null)
        BigDecimal changeRate,    // 전일 대비 변동액 (mid - previous, 기준값 없으면 null)
        BigDecimal changePercent  // 전일 대비 변동률(%) (기준값 없으면 null)
) {
    // 저장 정밀도(8자리)와 동일하게 노출 — 표시용 반올림(예: 2자리)은 프론트 영역
    private static final int RATE_SCALE = 8;
    // 변동률은 표시 목적의 비율값이라 2자리로 고정 노출
    private static final int PERCENT_SCALE = 2;
    // 나눗셈 중간 계산 정밀도 (퍼센트 반올림 전 손실 방지)
    private static final int DIVIDE_SCALE = 10;

    public static FxRateResponse from(FxRateSnapshot snapshot, BigDecimal previousRate) {
        BigDecimal mid = normalize(snapshot.midRate());
        BigDecimal previous = (previousRate == null) ? null : normalize(previousRate);
        return new FxRateResponse(
                snapshot.baseCurrency(),
                snapshot.quoteCurrency(),
                mid,
                normalize(snapshot.buyRate()),
                normalize(snapshot.sellRate()),
                snapshot.fetchedAt(),
                previous,
                changeRate(mid, previous),
                changePercent(mid, previous)
        );
    }

    // 파생값(매수/매도)의 BigDecimal 곱셈 잔재를 제거하고 mid와 자리수를 맞추기 위함
    private static BigDecimal normalize(BigDecimal rate) {
        return rate.setScale(RATE_SCALE, RoundingMode.HALF_UP);
    }

    // 전일 대비 변동액 (기준값이 없으면 변동 정보 자체를 노출하지 않음)
    private static BigDecimal changeRate(BigDecimal mid, BigDecimal previous) {
        if (previous == null) {
            return null;
        }
        return mid.subtract(previous);
    }

    // 전일 대비 변동률(%) = (mid - previous) / previous × 100. previous 가 0이면 0 나누기 회피 위해 null.
    private static BigDecimal changePercent(BigDecimal mid, BigDecimal previous) {
        if (previous == null || previous.signum() == 0) {
            return null;
        }
        return mid.subtract(previous)
                .divide(previous, DIVIDE_SCALE, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(PERCENT_SCALE, RoundingMode.HALF_UP);
    }
}
