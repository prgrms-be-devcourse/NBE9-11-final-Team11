package com.fxflow.domain.remittancetransaction.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;

public record RemittanceTransactionPageResponse(
        List<RemittanceTransactionSummaryResponse> data,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static RemittanceTransactionPageResponse from(
            Page<?> page,
            List<RemittanceTransactionSummaryResponse> data
    ) {
        return new RemittanceTransactionPageResponse(
                data,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
