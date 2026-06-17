package com.fxflow.domain.companypool.enums;

public enum RebalancingStatus {
    SUCCESS,
    RETRY_REQUIRED,   // race condition 등으로 풀 UPDATE 실패 → 스케줄러 재시도 대상
    MANUAL_REQUIRED   // 양 통화 모두 floor 미만 → 자동 복구 불가, 수동 개입 필요
}
