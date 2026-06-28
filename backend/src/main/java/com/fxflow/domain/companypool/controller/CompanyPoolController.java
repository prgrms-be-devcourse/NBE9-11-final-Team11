package com.fxflow.domain.companypool.controller;

import com.fxflow.domain.companypool.dto.request.RebalancingExecuteReq;
import com.fxflow.domain.companypool.dto.response.PoolDashboardRes;
import com.fxflow.domain.companypool.dto.response.RebalancingExecuteRes;
import com.fxflow.domain.companypool.dto.response.RebalancingHistoryPageRes;
import com.fxflow.domain.companypool.enums.TriggerType;
import com.fxflow.domain.companypool.repository.RebalancingRepository;
import com.fxflow.domain.companypool.service.CompanyPoolService;
import com.fxflow.domain.companypool.service.RebalancingService;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/pools")
public class CompanyPoolController {

    private final CompanyPoolService companyPoolService;
    private final RebalancingService rebalancingService;
    private final RebalancingRepository rebalancingRepository;

    @GetMapping("/dashboard")
    public ResponseEntity<PoolDashboardRes> getDashboard() {
        return ResponseEntity.ok(companyPoolService.getDashboard());
    }

    @PostMapping("/rebalance")
    public ResponseEntity<RebalancingExecuteRes> executeRebalancing(
            @RequestBody(required = false) RebalancingExecuteReq request
    ) {
        String reason = request != null ? request.reason() : null;
        RebalancingExecuteRes result = rebalancingService.execute(TriggerType.MANUAL, reason);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/rebalance/history")
    public ResponseEntity<RebalancingHistoryPageRes> getHistory(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size
    ) {
        return ResponseEntity.ok(
                RebalancingHistoryPageRes.from(
                        rebalancingRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size))
                )
        );
    }
}