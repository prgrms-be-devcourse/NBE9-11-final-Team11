package com.fxflow.domain.wallet.controller;

import com.fxflow.domain.wallet.dto.request.ChargeRequest;
import com.fxflow.domain.wallet.dto.request.WithdrawRequest;
import com.fxflow.domain.wallet.dto.response.TransactionHistoryResponse;
import com.fxflow.domain.wallet.dto.response.TransactionResponse;
import com.fxflow.domain.wallet.dto.response.WalletBalanceResponse;
import com.fxflow.domain.wallet.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/wallets")
public class WalletController {

    private final WalletService walletService;

    @GetMapping
    public ResponseEntity<WalletBalanceResponse> getWalletBalance(
            @AuthenticationPrincipal Long userId
    ){
        WalletBalanceResponse res = walletService.getWalletBalance(userId);
        return ResponseEntity.ok(res);
    }

    @GetMapping("/transactions")
    public ResponseEntity<TransactionHistoryResponse> getTransactionHistory(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) String currency,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to
    ){
        Pageable pageable = PageRequest.of(page, size, org.springframework.data.domain.Sort.by("createdAt").descending());
        TransactionHistoryResponse res = walletService.getTransactionHistory(userId, currency, from, to, pageable);
        return ResponseEntity.ok(res);
    }

    @PostMapping("/charge")
    public ResponseEntity<TransactionResponse> charge(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ChargeRequest request
    ){
        TransactionResponse res = walletService.charge(userId, request);
        return ResponseEntity.ok(res);
    }

    @PostMapping("/withdraw")
    public ResponseEntity<TransactionResponse> withdraw(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody WithdrawRequest request
    ){
        TransactionResponse res = walletService.withdraw(userId, request);
        return ResponseEntity.ok(res);
    }
}