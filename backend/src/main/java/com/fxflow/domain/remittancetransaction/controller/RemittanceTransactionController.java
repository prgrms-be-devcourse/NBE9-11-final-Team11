package com.fxflow.domain.remittancetransaction.controller;

import com.fxflow.domain.remittancetransaction.dto.response.RemittanceLimitResponse;
import com.fxflow.domain.remittancetransaction.service.RemittanceTransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/transfers")
@RequiredArgsConstructor
public class RemittanceTransactionController {

    private final RemittanceTransactionService remittanceTransactionService;

    @GetMapping("/limit")
    public ResponseEntity<RemittanceLimitResponse> getRemittanceLimit() {
        // TODO: 인증 레이어 완성 후 @AuthenticationPrincipal로 실제 유저 ID 주입받도록 수정할 것
        Long mockUserId = 1L;

        RemittanceLimitResponse response = remittanceTransactionService.getRemittanceLimit(mockUserId);
        return ResponseEntity.ok(response);
    }

    /*
    @GetMapping("/limit")
    public ResponseEntity<RemittanceLimitResponse> getRemittanceLimit(
            @AuthenticationPrincipal CustomUserDetails userDetails // 인증된 유저 객체 주입
    ) {
        Long userId = userDetails.getId(); // 토큰에서 추출한 실제 유저 ID 사용
        return ResponseEntity.ok(service.getRemittanceLimit(userId));
    }

     */

}