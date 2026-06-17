package com.fxflow.domain.wallet.controller;

import com.fxflow.domain.wallet.dto.request.ExchangeQuoteRequest;
import com.fxflow.domain.wallet.dto.response.ExchangeQuoteResponse;
import com.fxflow.domain.wallet.service.ExchangeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wallets")
@RequiredArgsConstructor
public class ExchangeController {

    private final ExchangeService exchangeService;

    @GetMapping("/exchange/quote")
    public ResponseEntity<ExchangeQuoteResponse> getExchangeQuote(
            @AuthenticationPrincipal Long userId,
            ExchangeQuoteRequest request
    ){
        ExchangeQuoteResponse res = exchangeService.getExchangeQuote(userId, request);
        return ResponseEntity.ok(res);
    }
}