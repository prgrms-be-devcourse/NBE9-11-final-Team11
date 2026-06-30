package com.fxflow.domain.remittancetransaction.enums;

public enum RemittanceRefundStatus {
    NONE,      // 환불 대상 아님 (정상 진행 중이거나 완료)
    PENDING,   // 환불 처리 대기 중
    REFUNDED   // 유저 모의계좌로 원화 환불 완료
}