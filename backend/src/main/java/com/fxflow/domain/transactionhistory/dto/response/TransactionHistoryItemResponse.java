package com.fxflow.domain.transactionhistory.dto.response;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fxflow.domain.ledger.entity.LedgerEntry;
import com.fxflow.domain.ledger.enums.LedgerDirection;
import com.fxflow.domain.ledger.enums.LedgerEntryType;
import com.fxflow.domain.ledger.enums.LedgerRefType;
import com.fxflow.domain.remittancetransaction.entity.RemittanceTransaction;
import com.fxflow.domain.wallet.entity.ExchangeTransaction;
import com.fxflow.domain.wallet.entity.P2pTransfer;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 공통 거래내역 한 줄 응답이다.
 * LedgerEntry를 기준으로 만들되, transactionType으로 화면에 필요한 거래 종류를 명확히 구분한다.
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "transactionType",
        visible = true
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = TransactionHistoryItemResponse.Simple.class, name = "CHARGE"),
        @JsonSubTypes.Type(value = TransactionHistoryItemResponse.Simple.class, name = "WITHDRAW"),
        @JsonSubTypes.Type(value = TransactionHistoryItemResponse.Exchange.class, name = "EXCHANGE"),
        @JsonSubTypes.Type(value = TransactionHistoryItemResponse.P2pTransferItem.class, name = "P2P_TRANSFER"),
        @JsonSubTypes.Type(value = TransactionHistoryItemResponse.Remittance.class, name = "REMITTANCE")
})
public sealed interface TransactionHistoryItemResponse
        permits TransactionHistoryItemResponse.Simple,
        TransactionHistoryItemResponse.Exchange,
        TransactionHistoryItemResponse.P2pTransferItem,
        TransactionHistoryItemResponse.Remittance {

    static TransactionHistoryItemResponse simple(LedgerEntry entry) {
        return Simple.from(entry);
    }

    static TransactionHistoryItemResponse exchange(LedgerEntry entry, ExchangeTransaction exchangeTransaction) {
        return Exchange.from(entry, exchangeTransaction);
    }

    static TransactionHistoryItemResponse p2pTransfer(LedgerEntry entry, P2pTransfer p2pTransfer) {
        return P2pTransferItem.from(entry, p2pTransfer);
    }

    static TransactionHistoryItemResponse remittance(
            LedgerEntry entry,
            RemittanceTransaction remittanceTransaction
    ) {
        return Remittance.from(entry, remittanceTransaction);
    }

    /**
     * 충전/출금처럼 LedgerEntry 자체 정보만으로 표현 가능한 거래 응답이다.
     */
    record Simple(
            TransactionHistoryItemType transactionType,
            String journalId,
            LedgerEntryType type,
            LedgerRefType refType,
            LedgerDirection direction,
            String currency,
            BigDecimal amount,
            BigDecimal balanceAfter,
            LocalDateTime createdAt
    ) implements TransactionHistoryItemResponse {

        public static Simple from(LedgerEntry entry) {
            return new Simple(
                    TransactionHistoryItemType.valueOf(entry.getEntryType().name()),
                    entry.getJournalId(),
                    entry.getEntryType(),
                    entry.getRefType(),
                    entry.getLedgerDirection(),
                    entry.getCurrencyCode(),
                    entry.getAmount(),
                    entry.getBalanceAfter(),
                    entry.getCreatedAt()
            );
        }
    }

    /**
     * 환전 거래는 LedgerEntry에 더해 환전 금액, 환율, 수수료 정보를 붙인다.
     */
    record Exchange(
            TransactionHistoryItemType transactionType,
            String journalId,
            LedgerEntryType type,
            LedgerDirection direction,
            String currency,
            String fromCurrency,
            String toCurrency,
            BigDecimal fromAmount,
            BigDecimal toAmount,
            BigDecimal exchangeRate,
            BigDecimal feeAmount,
            LocalDateTime createdAt
    ) implements TransactionHistoryItemResponse {

        public static Exchange from(LedgerEntry entry, ExchangeTransaction exchangeTransaction) {
            return new Exchange(
                    TransactionHistoryItemType.EXCHANGE,
                    entry.getJournalId(),
                    entry.getEntryType(),
                    entry.getLedgerDirection(),
                    entry.getCurrencyCode(),
                    exchangeTransaction.getFromCurrencyCode(),
                    exchangeTransaction.getToCurrencyCode(),
                    exchangeTransaction.getFromAmount(),
                    exchangeTransaction.getToAmount(),
                    exchangeTransaction.getFinalRate(),
                    exchangeTransaction.getFeeAmount(),
                    entry.getCreatedAt()
            );
        }
    }

    /**
     * P2P 이체는 LedgerEntry.type이 TRANSFER이므로 transactionType으로 해외송금과 구분한다.
     */
    record P2pTransferItem(
            TransactionHistoryItemType transactionType,
            String journalId,
            LedgerEntryType type,
            LedgerRefType refType,
            LedgerDirection direction,
            String currency,
            BigDecimal amount,
            String counterpartyEmail,
            String memo,
            LocalDateTime createdAt
    ) implements TransactionHistoryItemResponse {

        public static P2pTransferItem from(LedgerEntry entry, P2pTransfer p2pTransfer) {
            String counterpartyEmail = entry.getLedgerDirection() == LedgerDirection.DEBIT
                    ? p2pTransfer.getToWallet().getUser().getEmail()
                    : p2pTransfer.getFromWallet().getUser().getEmail();

            return new P2pTransferItem(
                    TransactionHistoryItemType.P2P_TRANSFER,
                    entry.getJournalId(),
                    entry.getEntryType(),
                    entry.getRefType(),
                    entry.getLedgerDirection(),
                    p2pTransfer.getCurrencyCode(),
                    entry.getAmount(),
                    counterpartyEmail,
                    p2pTransfer.getMemo(),
                    entry.getCreatedAt()
            );
        }
    }

    /**
     * 해외송금은 송금자 모의계좌 출금 LedgerEntry를 대표 거래로 사용하고 송금 상세 정보를 붙인다.
     */
    record Remittance(
            TransactionHistoryItemType transactionType,
            String journalId,
            LedgerEntryType type,
            LedgerRefType refType,
            LedgerDirection direction,
            String currency,
            BigDecimal amount,
            BigDecimal balanceAfter,
            Long remittanceId,
            String recipientName,
            String recipientBankName,
            String recipientAccountNumber,
            String status,
            BigDecimal receiveAmount,
            String receiveCurrency,
            LocalDateTime createdAt
    ) implements TransactionHistoryItemResponse {

        public static Remittance from(LedgerEntry entry, RemittanceTransaction remittanceTransaction) {
            return new Remittance(
                    TransactionHistoryItemType.REMITTANCE,
                    entry.getJournalId(),
                    entry.getEntryType(),
                    entry.getRefType(),
                    entry.getLedgerDirection(),
                    entry.getCurrencyCode(),
                    entry.getAmount(),
                    entry.getBalanceAfter(),
                    remittanceTransaction.getId(),
                    remittanceTransaction.getRecipientName(),
                    remittanceTransaction.getRecipientBankName(),
                    remittanceTransaction.getRecipientAccountNumber(),
                    remittanceTransaction.getStatus().name(),
                    remittanceTransaction.getReceiveAmount(),
                    remittanceTransaction.getReceiveCurrency(),
                    entry.getCreatedAt()
            );
        }
    }
}
