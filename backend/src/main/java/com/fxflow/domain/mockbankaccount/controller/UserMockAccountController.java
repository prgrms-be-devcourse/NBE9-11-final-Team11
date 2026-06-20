package com.fxflow.domain.mockbankaccount.controller;

import com.fxflow.domain.mockbankaccount.dto.response.MockBankAccountResponse;
import com.fxflow.domain.mockbankaccount.service.MockBankAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users/me")
public class UserMockAccountController {

    private final MockBankAccountService mockBankAccountService;

    /**
     * 로그인한 사용자의 KRW 모의계좌 잔액을 조회한다.
     * GET /api/v1/users/me/mock-account
     */
    @GetMapping("/mock-account")
    public ResponseEntity<MockBankAccountResponse> getMyMockAccount(
            @AuthenticationPrincipal Long userId
    ) {
        MockBankAccountResponse response = mockBankAccountService.getMyAccount(userId);
        return ResponseEntity.ok(response);
    }
}