package com.fxflow.domain.fxrate.enums;

import com.fxflow.domain.fxrate.exception.FxRateErrorCode;
import com.fxflow.global.exception.BusinessException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.Duration;
import java.util.Locale;

/**
 * 환율 이력 조회 기간. 조회 윈도우(lookback)와 다운샘플링 버킷 단위를 함께 보유한다.
 * 2분 주기 수집이므로 그대로 반환하면 1주일 ~5,040건·1달 ~21,600건이라 차트가 무거워진다.
 * → 1W/1M은 PostgreSQL date_trunc 버킷 평균으로 집계해 수백 건 이하로 캡한다.
 *   1D는 분 버킷이라 사실상 원본(~720건)을 그대로 노출한다(차트 렌더링엔 무리 없는 수준).
 */
@Getter
@RequiredArgsConstructor
public enum FxRateHistoryPeriod {

    ONE_DAY("1D", Duration.ofDays(1), "minute"), // ~720건 (2분 간격을 분 버킷으로 그대로 노출)
    ONE_WEEK("1W", Duration.ofDays(7), "hour"),  // ~168건 (시간 버킷 평균)
    ONE_MONTH("1M", Duration.ofDays(30), "day"); // ~30건 (일 버킷 평균)

    private final String code;        // 요청 파라미터 값 (1D / 1W / 1M)
    private final Duration lookback;  // 조회 윈도우 ((now - lookback) ~ now)
    private final String bucketUnit;  // PostgreSQL date_trunc 단위

    // 요청 파라미터 문자열을 enum으로 변환 (대소문자 무시). 유효하지 않으면 400(F-003).
    public static FxRateHistoryPeriod from(String code) {
        if (code != null) {
            String normalized = code.toUpperCase(Locale.ROOT);
            for (FxRateHistoryPeriod period : values()) {
                if (period.code.equals(normalized)) {
                    return period;
                }
            }
        }
        throw new BusinessException(FxRateErrorCode.FX_RATE_INVALID_PERIOD);
    }
}
