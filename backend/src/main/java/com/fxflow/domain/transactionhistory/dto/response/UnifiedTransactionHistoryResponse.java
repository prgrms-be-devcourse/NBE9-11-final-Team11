package com.fxflow.domain.transactionhistory.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 공통 거래내역 페이지 응답이다.
 */
public record UnifiedTransactionHistoryResponse(
        List<TransactionHistoryItemResponse> data,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static UnifiedTransactionHistoryResponse from(Page<TransactionHistoryItemResponse> page) {
        return new UnifiedTransactionHistoryResponse(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
