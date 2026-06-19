package com.fxflow.domain.companypool.controller;

import com.fxflow.domain.companypool.dto.request.RebalancingExecuteReq;
import com.fxflow.domain.companypool.dto.response.PoolDashboardRes;
import com.fxflow.domain.companypool.dto.response.RebalancingExecuteRes;
import com.fxflow.domain.companypool.dto.response.RebalancingHistoryRes;
import com.fxflow.domain.companypool.enums.TriggerType;
import com.fxflow.domain.companypool.repository.RebalancingRepository;
import com.fxflow.domain.companypool.service.CompanyPoolService;
import com.fxflow.domain.companypool.service.RebalancingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
    public ResponseEntity<List<RebalancingHistoryRes>> getHistory() {
        List<RebalancingHistoryRes> history = rebalancingRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(RebalancingHistoryRes::from)
                .toList();
        return ResponseEntity.ok(history);
    }
}