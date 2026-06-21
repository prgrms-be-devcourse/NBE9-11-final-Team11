package com.fxflow.domain.mockbankaccount.controller;

import com.fxflow.domain.mockbankaccount.dto.response.MockBankAccountResponse;
import com.fxflow.domain.mockbankaccount.service.MockBankAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "모의계좌", description = "모의계좌(KRW) 연결 및 사전 검증 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users/me")
public class UserMockAccountController {

    private final MockBankAccountService mockBankAccountService;

    @Operation(
            summary = "내 모의계좌(KRW) 잔액 조회",
            description = "로그인한 사용자의 연결된 KRW 모의계좌 정보(은행명, 계좌번호, 잔액)를 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 토큰"),
            @ApiResponse(responseCode = "404", description = "연결된 모의계좌가 없음")
    })
    @GetMapping("/mock-account")
    public ResponseEntity<MockBankAccountResponse> getMyMockAccount(
            @AuthenticationPrincipal Long userId
    ) {
        MockBankAccountResponse response = mockBankAccountService.getMyAccount(userId);
        return ResponseEntity.ok(response);
    }
}