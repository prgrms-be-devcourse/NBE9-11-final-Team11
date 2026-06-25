package com.fxflow.domain.transactionhistory.dto.response;

/**
 * 화면에서 거래 종류를 구분하기 위한 공통 거래 타입이다.
 * LedgerEntryType.TRANSFER만으로는 P2P와 해외송금을 구분하기 어려워 별도 타입을 둔다.
 */
public enum TransactionHistoryItemType {
    CHARGE,
    WITHDRAW,
    EXCHANGE,
    P2P_TRANSFER,
    REMITTANCE
}
