package com.fxflow.domain.mockbankaccount.controller;

import com.fxflow.domain.mockbankaccount.dto.request.MockBankAccountCheckRequest;
import com.fxflow.domain.mockbankaccount.dto.request.MockBankLinkRequest;
import com.fxflow.domain.mockbankaccount.dto.response.MockBankAccountCheckResponse;
import com.fxflow.domain.mockbankaccount.dto.response.MockBankLinkResponse;
import com.fxflow.domain.mockbankaccount.service.MockBankAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/mockbank")
class MockBankAccountController {

    private final MockBankAccountService mockBankAccountService;
    /*
     * 계좌번호 사전 확인 (형식 + 전역 중복)
     * 회원가입 KYC 단계 등 로그인 전 상태에서도 호출 가능해야 하므로 인증이 필요 없다.
     * POST /api/v1/mockbank/check
     */
    @PostMapping("/check")
    public ResponseEntity<MockBankAccountCheckResponse> checkAccountNumber(
            @Valid @RequestBody MockBankAccountCheckRequest request
    ) {
        MockBankAccountCheckResponse response = mockBankAccountService.checkAccountNumber(request.accountNumber());
        return ResponseEntity.ok(response);
    }

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