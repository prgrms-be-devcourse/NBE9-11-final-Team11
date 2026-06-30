package com.fxflow.domain.wallet.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;

public record TransactionHistoryResponse(
        long totalCount,
        List<TransactionResponse> transactionResponseList
) {
    public static TransactionHistoryResponse from(Page<TransactionResponse> transactionsPage) {
        return new TransactionHistoryResponse(
                transactionsPage.getTotalElements(),
                transactionsPage.getContent()
        );
    }
}
