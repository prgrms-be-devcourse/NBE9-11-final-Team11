package com.fxflow.domain.wallet.dto.response;

import com.fxflow.domain.ledger.entity.LedgerEntry;
import com.fxflow.domain.ledger.enums.LedgerDirection;
import com.fxflow.domain.ledger.enums.LedgerEntryType;
import com.fxflow.global.util.CurrencyAmountFormatter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionResponse (
    Long transactionId,
    LedgerEntryType type,
    LedgerDirection direction,
    String currency,
    BigDecimal amount,
    BigDecimal balanceAfter,
    LocalDateTime createdAt
) {
    public static TransactionResponse from(LedgerEntry ledgerEntry) {
        return new TransactionResponse(
                ledgerEntry.getId(),
                ledgerEntry.getEntryType(),
                ledgerEntry.getLedgerDirection(),
                ledgerEntry.getCurrencyCode(),
                CurrencyAmountFormatter.format(ledgerEntry.getAmount(), ledgerEntry.getCurrencyCode()),
                CurrencyAmountFormatter.format(ledgerEntry.getBalanceAfter(), ledgerEntry.getCurrencyCode()),
                ledgerEntry.getCreatedAt()
        );
    }
}
