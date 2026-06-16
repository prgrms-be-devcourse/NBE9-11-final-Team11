package com.fxflow.domain.mockbankaccount.controller;

import com.fxflow.domain.mockbankaccount.dto.request.MockBankLinkRequest;
import com.fxflow.domain.mockbankaccount.dto.response.MockBankLinkResponse;
import com.fxflow.domain.mockbankaccount.service.MockBankAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/mockbank")
class MockBankAccountController {

    private final MockBankAccountService mockBankAccountService;

    /**
     * 모의계좌(KRW) 연결 + KRW/USD Wallet 생성
     * POST /api/v1/mockbank/link
     */
    @PostMapping("/link")
    public ResponseEntity<MockBankLinkResponse> linkAccount(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody MockBankLinkRequest request
    ) {
        MockBankLinkResponse response = mockBankAccountService.linkAccount(
                userId, request.bankName(), request.accountNumber()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}