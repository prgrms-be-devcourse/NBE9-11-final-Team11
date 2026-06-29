package com.fxflow.domain.fxrate.dto.response;

import com.fxflow.domain.fxrate.enums.FxRateHistoryPeriod;
import com.fxflow.domain.fxrate.repository.FxRateHistoryRow;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 환율 이력 조회 응답. 기간별 버킷 시계열(points)을 시간 오름차순으로 노출한다.
 * 데이터가 없으면 points 가 빈 배열이며(404 아님), 표시용 라벨 포맷은 프론트 영역이다.
 */
public record FxRateHistoryResponse(
        String baseCurrency,   // 기준 통화
        String quoteCurrency,  // 상대 통화
        String period,         // 조회 기간 (1D / 1W / 1M)
        List<Point> points     // 시계열 (시간 오름차순)
) {
    // 저장 정밀도(8자리)와 동일하게 노출 — 표시용 반올림은 프론트 영역
    private static final int RATE_SCALE = 8;

    public record Point(
            LocalDateTime timestamp, // 버킷 시작 시각
            BigDecimal midRate       // 버킷 평균 mid 환율
    ) {}

    public static FxRateHistoryResponse of(
            String baseCurrency, String quoteCurrency, FxRateHistoryPeriod period, List<FxRateHistoryRow> rows) {
        List<Point> points = rows.stream()
                // AVG 집계로 잔여 소수가 생기므로 mid 자리수(8)에 맞춰 정규화
                .map(row -> new Point(row.getBucket(), row.getRate().setScale(RATE_SCALE, RoundingMode.HALF_UP)))
                .toList();
        return new FxRateHistoryResponse(baseCurrency, quoteCurrency, period.getCode(), points);
    }
}
