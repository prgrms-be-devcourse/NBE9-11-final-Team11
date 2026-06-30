package com.fxflow.domain.transactionhistory.controller;

import com.fxflow.domain.ledger.enums.LedgerEntryType;
import com.fxflow.domain.transactionhistory.dto.response.UnifiedTransactionHistoryResponse;
import com.fxflow.domain.transactionhistory.service.TransactionHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/transactions")
public class TransactionHistoryController {

    private final TransactionHistoryService transactionHistoryService;

    /**
     * 지갑 거래와 해외송금 거래를 한 화면에서 보여주기 위한 공통 거래내역 API다.
     */
    @GetMapping
    public ResponseEntity<UnifiedTransactionHistoryResponse> getTransactionHistory(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) LedgerEntryType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        UnifiedTransactionHistoryResponse response = transactionHistoryService.getTransactionHistory(
                userId,
                currency,
                type,
                from,
                to,
                pageable
        );

        return ResponseEntity.ok(response);
    }
}
