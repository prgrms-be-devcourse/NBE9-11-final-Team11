package com.fxflow.domain.wallet.controller;

import com.fxflow.domain.wallet.dto.response.WalletRes;
import com.fxflow.domain.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class WalletController {

    private final WalletService walletService;

    @GetMapping("/wallets")
    public ResponseEntity<WalletRes> getWallets(){
        WalletRes res = walletService.getWallets();
        return ResponseEntity.ok(res);
    }



}