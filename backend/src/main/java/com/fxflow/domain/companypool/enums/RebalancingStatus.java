package com.fxflow.domain.companypool.enums;

public enum RebalancingStatus {
    SUCCESS,
    FAILED,           // DB 장애 등 인프라 문제로 풀 UPDATE 실패
    MANUAL_REQUIRED   // 양 통화 모두 floor 미만 → 자동 복구 불가, 수동 개입 필요
}
