package com.fxflow.domain.mockbankaccount.dto.response;

import org.springframework.data.domain.Page;
import java.util.List;

public record PagedReceiptResponse(
        List<RemittanceReceiptDto> content, // 실제 데이터 리스트
        int currentPage,                    // 현재 페이지 번호 (0부터 시작)
        int totalPages,                     // 전체 페이지 수
        long totalElements                  // 전체 데이터 개수
) {
    public static PagedReceiptResponse from(Page<RemittanceReceiptDto> page) {
        return new PagedReceiptResponse(
                page.getContent(),
                page.getNumber(),
                page.getTotalPages(),
                page.getTotalElements()
        );
    }
}