package com.fxflow.domain.fxrate.dto.response;

import java.math.BigDecimal;

/**
 * Twelve Data exchange_rate API 응답 매핑 DTO (외부 API → 서버)
 *
 */
public record TwelveDataResponse(
        String symbol,      // 통화쌍 (예: USD/KRW)
        BigDecimal rate,    // 기준 환율
        Long timestamp      // 응답 시각 (epoch seconds)
) {
}
