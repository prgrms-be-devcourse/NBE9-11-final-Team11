package com.fxflow.domain.fxrate.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 이력 버킷 집계 결과 행 (네이티브 쿼리 인터페이스 프로젝션).
 * date_trunc 결과(bucket)와 버킷 내 평균 환율(rate)을 담는다.
 */
public interface FxRateHistoryRow {
    LocalDateTime getBucket(); // 버킷 시작 시각 (date_trunc 결과)
    BigDecimal getRate();      // 버킷 평균 mid 환율
}
