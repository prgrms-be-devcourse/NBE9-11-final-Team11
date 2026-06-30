package com.fxflow.domain.reservation.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 예약 목록 페이지 응답 (RSV-07). 송금 내역 목록 응답과 동일한 형식을 따른다.
 */
public record ReservationPageResponse(
        List<ReservationResponse> data,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static ReservationPageResponse from(Page<?> page, List<ReservationResponse> data) {
        return new ReservationPageResponse(
                data,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
