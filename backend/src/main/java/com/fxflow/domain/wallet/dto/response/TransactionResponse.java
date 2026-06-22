package com.fxflow.domain.wallet.dto.response;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fxflow.domain.ledger.entity.LedgerEntry;
import com.fxflow.domain.ledger.enums.LedgerDirection;
import com.fxflow.domain.ledger.enums.LedgerEntryType;
import com.fxflow.domain.ledger.enums.LedgerRefType;
import com.fxflow.domain.wallet.entity.ExchangeTransaction;
import com.fxflow.domain.wallet.entity.P2pTransfer;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "type", // LedgerEntryType 필드를 기준으로 JSON 타입을 구분
        visible = true
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = TransactionResponse.Simple.class, name = "CHARGE"),
        @JsonSubTypes.Type(value = TransactionResponse.Simple.class, name = "WITHDRAW"),
        @JsonSubTypes.Type(value = TransactionResponse.Exchange.class, name = "EXCHANGE"),
        @JsonSubTypes.Type(value = TransactionResponse.Transfer.class, name = "TRANSFER")
})
public sealed interface TransactionResponse
        permits TransactionResponse.Simple,
        TransactionResponse.Exchange,
        TransactionResponse.Transfer {

    LedgerEntryType type(); // 공통 식별자 (Discriminator)

    static TransactionResponse from(LedgerEntry entry) {
        return Simple.from(entry);
    }

    static TransactionResponse from(LedgerEntry entry, ExchangeTransaction exchangeTx) {
        return Exchange.from(entry, exchangeTx);
    }

    static TransactionResponse from(LedgerEntry entry, P2pTransfer transferTx) {
        return Transfer.from(entry, transferTx);
    }

    // 1. 단순 입출금 (CHARGE, WITHDRAW)
    record Simple(
            String journalId,
            LedgerEntryType type,
            LedgerRefType refType,
            LedgerDirection direction,
            String currency,
            BigDecimal amount,
            BigDecimal balanceAfter,
            LocalDateTime createdAt
    ) implements TransactionResponse {
        public static Simple from(LedgerEntry entry) {
            return new Simple(
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

    // 2. 환전 (EXCHANGE)
    record Exchange(
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
    ) implements TransactionResponse {
        public static Exchange from(LedgerEntry entry, ExchangeTransaction exchangeTx) {
            return new Exchange(
                    entry.getJournalId(),
                    entry.getEntryType(),
                    entry.getLedgerDirection(),
                    entry.getCurrencyCode(),
                    exchangeTx.getFromCurrencyCode(),
                    exchangeTx.getToCurrencyCode(),
                    exchangeTx.getFromAmount(),
                    exchangeTx.getToAmount(),
                    exchangeTx.getFinalRate(),
                    exchangeTx.getFeeAmount(),
                    entry.getCreatedAt()
            );
        }
    }

    // 3. P2P 이체 (TRANSFER)
    record Transfer(
            String journalId,
            LedgerEntryType type,
            LedgerDirection direction,
            String currency,
            BigDecimal amount,
            String counterpartyEmail,
            String memo,
            LocalDateTime createdAt
    ) implements TransactionResponse {
        public static Transfer from(LedgerEntry entry, P2pTransfer transfer){

            // 내 원장 기록이 DEBIT(출금)이면 받는 사람의 이메일을,
            // CREDIT(입금)이면 보낸 사람의 이메일을 가져옵니다.
            String counterpartyEmail = (entry.getLedgerDirection() == LedgerDirection.DEBIT)
                    ? transfer.getToWallet().getUser().getEmail()
                    : transfer.getFromWallet().getUser().getEmail();

            return new Transfer(
                    entry.getJournalId(),
                    entry.getEntryType(),
                    entry.getLedgerDirection(),
                    transfer.getCurrencyCode(),
                    entry.getAmount(),
                    counterpartyEmail,
                    transfer.getMemo(),
                    entry.getCreatedAt()
            );
        }
    }
}