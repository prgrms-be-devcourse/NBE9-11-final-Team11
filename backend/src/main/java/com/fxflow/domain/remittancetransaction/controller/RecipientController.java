package com.fxflow.domain.remittancetransaction.controller;

import com.fxflow.domain.remittancetransaction.dto.request.RecipientCreateRequest;
import com.fxflow.domain.remittancetransaction.dto.response.RecipientResponse;
import com.fxflow.domain.remittancetransaction.service.RecipientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class RecipientController {

    private final RecipientService recipientService;

    /**
     * 로그인한 사용자의 해외 수취인을 등록한다.
     */
    @PostMapping("/recipients")
    public ResponseEntity<RecipientResponse> createRecipient(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody RecipientCreateRequest request
    ) {
        RecipientResponse response = recipientService.createRecipient(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 로그인한 사용자가 등록한 해외 수취인 목록을 조회한다.
     */
    @GetMapping("/recipients")
    public ResponseEntity<List<RecipientResponse>> getRecipients(
            @AuthenticationPrincipal Long userId
    ) {
        List<RecipientResponse> response = recipientService.getRecipients(userId);
        return ResponseEntity.ok(response);
    }

    /**
     * 로그인한 사용자의 해외 수취인을 삭제한다.
     * 실제 데이터는 남겨 과거 송금 내역의 참조가 깨지지 않도록 한다.
     */
    @DeleteMapping("/recipients/{recipientId}")
    public ResponseEntity<Void> deleteRecipient(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long recipientId
    ) {
        recipientService.deleteRecipient(userId, recipientId);
        return ResponseEntity.noContent().build();
    }
}
