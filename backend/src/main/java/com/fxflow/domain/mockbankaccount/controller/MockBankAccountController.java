package com.fxflow.domain.mockbankaccount.controller;

import com.fxflow.domain.mockbankaccount.dto.request.MockBankAccountCheckRequest;
import com.fxflow.domain.mockbankaccount.dto.request.MockBankLinkRequest;
import com.fxflow.domain.mockbankaccount.dto.response.MockBankAccountCheckResponse;
import com.fxflow.domain.mockbankaccount.dto.response.MockBankLinkResponse;
import com.fxflow.domain.mockbankaccount.service.MockBankAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;


@Tag(name = "모의계좌", description = "모의계좌(KRW) 연결 및 사전 검증 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/mockbank")
class MockBankAccountController {

    private final MockBankAccountService mockBankAccountService;
    @Operation(
            summary = "계좌번호 사전 확인",
            description = "계좌번호의 형식과 전역 중복 여부를 미리 확인합니다. 회원가입 KYC 단계 등 로그인 전 상태에서도 호출 가능합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "확인 완료 (available 필드로 가용 여부 판단)")
    })
    @PostMapping("/check")
    public ResponseEntity<MockBankAccountCheckResponse> checkAccountNumber(
            @Valid @RequestBody MockBankAccountCheckRequest request
    ) {
        MockBankAccountCheckResponse response = mockBankAccountService.checkAccountNumber(request.accountNumber());
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "모의계좌(KRW) 연결",
            description = "로그인한 사용자의 KRW 모의계좌를 연결합니다. 연결 시 1,000만원이 자동 입금되고 KRW/USD 월렛이 함께 생성됩니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "연결 성공"),
            @ApiResponse(responseCode = "400", description = "계좌번호 형식 불일치"),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 토큰"),
            @ApiResponse(responseCode = "409", description = "이미 연결된 모의계좌가 있음 / 계좌번호 중복")
    })
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