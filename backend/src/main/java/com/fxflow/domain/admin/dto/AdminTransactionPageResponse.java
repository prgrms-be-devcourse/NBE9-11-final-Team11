package com.fxflow.domain.admin.dto;

import java.util.List;

public record AdminTransactionPageResponse(
        List<AdminTransactionItem> data,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static AdminTransactionPageResponse of(List<AdminTransactionItem> data, int page, int size, long totalElements) {
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new AdminTransactionPageResponse(data, page, size, totalElements, totalPages);
    }
}
