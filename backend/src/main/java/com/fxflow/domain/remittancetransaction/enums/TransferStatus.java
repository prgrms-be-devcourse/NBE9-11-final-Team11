package com.fxflow.domain.remittancetransaction.enums;

public enum TransferStatus {
    PENDING, // 송금 신청 완료 상태
    FUNDED, // 가상계좌 입금 확인 완료 상태
    PROCESSING, // 수취인 계좌로 외화를 송금하고 있는 과정
    COMPLETED, // 수취인에게 돈이 정상적으로 전달된 상태
    FAILED, // 송금 실패 후 환불까지 완료된 상태
    REFUND_FAILED, // 송금 실패 후 환불 처리까지 실패하여 운영자 확인이 필요한 상태
    CANCELED // 유저가 송금 요청을 스스로 취소한 상태
}
