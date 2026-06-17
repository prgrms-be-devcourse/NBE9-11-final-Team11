package com.fxflow.domain.remittancetransaction.controller;

import com.fxflow.domain.remittancetransaction.dto.request.RemittanceTransactionCreateRequest;
import com.fxflow.domain.remittancetransaction.dto.response.RemittanceLimitResponse;
import com.fxflow.domain.remittancetransaction.dto.response.RemittanceMockFundedResponse;
import com.fxflow.domain.remittancetransaction.dto.response.RemittanceTransactionCreateResponse;
import com.fxflow.domain.remittancetransaction.service.RemittanceTransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class RemittanceTransactionController {

    private final RemittanceTransactionService remittanceTransactionService;

    /**
     * 로그인한 사용자의 해외송금 한도를 조회한다.
     */
    @GetMapping("/transfers/limit")
    public ResponseEntity<RemittanceLimitResponse> getRemittanceLimit(
            @AuthenticationPrincipal Long userId
    ) {
        RemittanceLimitResponse response = remittanceTransactionService.getRemittanceLimit(userId);
        return ResponseEntity.ok(response);
    }

    /**
     * 송금 사유를 포함해 해외송금 주문을 생성하고 가상계좌를 발급한다.
     */
    @PostMapping("/transfers")
    public ResponseEntity<RemittanceTransactionCreateResponse> createTransfer(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody RemittanceTransactionCreateRequest request
    ) {
        RemittanceTransactionCreateResponse response =
                remittanceTransactionService.createTransfer(userId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Mock 입금 확인을 통해 송금 주문을 FUNDED 상태로 변경한다.
     */
    @PostMapping("/transfers/{transferId}/mock-funded")
    public ResponseEntity<RemittanceMockFundedResponse> mockFundTransfer(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long transferId
    ) {
        RemittanceMockFundedResponse response =
                remittanceTransactionService.mockFundTransfer(userId, transferId);

        return ResponseEntity.ok(response);
    }
}