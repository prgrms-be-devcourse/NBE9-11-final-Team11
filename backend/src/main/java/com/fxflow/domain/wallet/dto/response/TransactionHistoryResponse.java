package com.fxflow.domain.wallet.dto.response;

import java.util.List;

public record TransactionHistoryResponse (
    Long totalCount,
    List<TransactionResponse> transactionResponseList
) {
    public static TransactionHistoryResponse from(Long totalCount, List<TransactionResponse> transactionResponseList) {
        return new TransactionHistoryResponse(totalCount, transactionResponseList);
    }
}
