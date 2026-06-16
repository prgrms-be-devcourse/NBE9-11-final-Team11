package com.fxflow.domain.wallet.dto.response;

import com.fxflow.domain.ledger.entity.LedgerEntry;
import org.springframework.data.domain.Page;

import java.util.List;

public record TransactionHistoryResponse (
    Long totalCount,
    List<TransactionResponse> transactionResponseList
) {
    public static TransactionHistoryResponse from(Page<LedgerEntry> entries) {
        List<TransactionResponse> transactionList = entries.getContent().stream()
                .map(TransactionResponse::from)
                .toList();
        return new TransactionHistoryResponse(entries.getTotalElements(), transactionList);
    }
}
