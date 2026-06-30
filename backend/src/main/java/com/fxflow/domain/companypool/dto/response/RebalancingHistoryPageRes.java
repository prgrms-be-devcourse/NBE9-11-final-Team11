package com.fxflow.domain.companypool.dto.response;

import com.fxflow.domain.companypool.entity.RebalancingOrder;
import java.util.List;
import org.springframework.data.domain.Page;

public record RebalancingHistoryPageRes(
        List<RebalancingHistoryRes> data,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static RebalancingHistoryPageRes from(Page<RebalancingOrder> pageResult) {
        return new RebalancingHistoryPageRes(
                pageResult.getContent().stream().map(RebalancingHistoryRes::from).toList(),
                pageResult.getNumber(),
                pageResult.getSize(),
                pageResult.getTotalElements(),
                pageResult.getTotalPages()
        );
    }
}
