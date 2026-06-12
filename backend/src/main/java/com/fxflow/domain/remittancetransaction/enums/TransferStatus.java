package com.fxflow.domain.remittancetransaction.enums;

public enum TransferStatus {
    PENDING, // 송금 신청 완료 상태
    FUNDED, // 가상계좌 입금 확인 완료 상태
    PROCESSING, // 수취인 계좌로 외화를 송금하고 있는 과정
    COMPLETED, // 수취인에게 돈이 정상적으로 전달된 상태
    FAILED, // 파트너사 통신 실패, 수취 계좌 정보 오류 등으로 송금이 실패한 상태
    CANCELED // 유저가 송금 요청을 스스로 취소한 상태
}