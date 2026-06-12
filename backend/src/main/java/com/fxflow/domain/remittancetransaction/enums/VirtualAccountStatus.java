package com.fxflow.domain.remittancetransaction.enums;

public enum VirtualAccountStatus {
    ISSUED,   // 가상계좌 발급 완료 (입금 대기)
    PAID,     // 송금자 원화 입금 확인 완료
    EXPIRED,  // 입금 기한 초과로 계좌 무효화
    CANCELED  // 송금 취소로 인한 계좌 폐기
}