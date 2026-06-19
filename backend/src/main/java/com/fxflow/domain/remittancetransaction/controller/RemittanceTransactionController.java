package com.fxflow.domain.remittancetransaction.controller;

import com.fxflow.domain.remittancetransaction.dto.request.RemittanceTransactionCreateRequest;
import com.fxflow.domain.remittancetransaction.dto.request.RemittanceTransactionQuoteRequest;
import com.fxflow.domain.remittancetransaction.dto.response.*;
import com.fxflow.domain.remittancetransaction.service.RemittanceTransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
     * 로그인한 사용자의 해외송금 내역 목록을 조회한다.
     */
    @GetMapping("/transfers")
    public ResponseEntity<List<RemittanceTransactionSummaryResponse>> getTransfers(
            @AuthenticationPrincipal Long userId
    ) {
        List<RemittanceTransactionSummaryResponse> response =
                remittanceTransactionService.getTransfers(userId);

        return ResponseEntity.ok(response);
    }

    /**
     * 로그인한 사용자의 특정 해외송금 내역을 상세 조회한다.
     */
    @GetMapping("/transfers/{transferId}")
    public ResponseEntity<RemittanceTransactionDetailResponse> getTransfer(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long transferId
    ) {
        RemittanceTransactionDetailResponse response =
                remittanceTransactionService.getTransfer(userId, transferId);

        return ResponseEntity.ok(response);
    }

    /**
     * 송금 사유를 포함해 해외송금 주문을 생성하고 가상계좌를 발급한다.
     */
    @PostMapping("/transfers")
    public ResponseEntity<RemittanceTransactionCreateResponse> createTransfer(
            @AuthenticationPrincipal Long userId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody RemittanceTransactionCreateRequest request
    ) {
        RemittanceTransactionCreateResponse response =
                remittanceTransactionService.createTransfer(userId, request, idempotencyKey);

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

    /**
     * 수취인과 송금 금액을 기준으로 해외송금 견적을 산출한다.
     */
    @PostMapping("/transfers/quote")
    public ResponseEntity<RemittanceTransactionQuoteResponse> createQuote(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody RemittanceTransactionQuoteRequest request
    ) {
        RemittanceTransactionQuoteResponse response =
                remittanceTransactionService.createQuote(userId, request);

        return ResponseEntity.ok(response);
    }
}
