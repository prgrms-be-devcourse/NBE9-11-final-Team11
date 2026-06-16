package com.fxflow.domain.wallet.controller;

import com.fxflow.domain.wallet.dto.request.ChargeRequest;
import com.fxflow.domain.wallet.dto.request.WithdrawRequest;
import com.fxflow.domain.wallet.dto.response.TransactionHistoryResponse;
import com.fxflow.domain.wallet.dto.response.TransactionResponse;
import com.fxflow.domain.wallet.dto.response.WalletBalanceResponse;
import com.fxflow.domain.wallet.service.P2pTransferService;
import com.fxflow.domain.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/wallets")
public class WalletController {

    private final WalletService walletService;
    private final P2pTransferService p2pTransferService;

    // todo: userDetails
    @GetMapping
    public ResponseEntity<WalletBalanceResponse> getWalletBalance(
            @AuthenticationPrincipal UserDetails userDetails
    ){
        WalletBalanceResponse res = walletService.getWalletBalance(1L);
        return ResponseEntity.ok(res);
    }

    // todo: userDetails
    @GetMapping("/transactions")
    public ResponseEntity<TransactionHistoryResponse> getTransactionHistory(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) String currency,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to
    ){
        Long userId = 1L;
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        TransactionHistoryResponse res = walletService.getTransactionHistory(1L, currency, from, to, pageable);
        return ResponseEntity.ok(res);
    }

    @PostMapping("/charge")
    public ResponseEntity<TransactionResponse> charge(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody ChargeRequest request
    ){
        Long userId = 1L;
        TransactionResponse res = walletService.charge(userId, request);
        return ResponseEntity.ok(res);
    }

    @PostMapping("/withdraw")
    public ResponseEntity<TransactionResponse> withdraw(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody WithdrawRequest request
    ){
        Long userId = 1L;
        TransactionResponse res = walletService.withdraw(userId, request);
        return ResponseEntity.ok(res);
    }
}