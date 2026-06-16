package com.fxflow.domain.remittancetransaction.controller;

import com.fxflow.domain.remittancetransaction.dto.request.RecipientCreateRequest;
import com.fxflow.domain.remittancetransaction.dto.response.RecipientResponse;
import com.fxflow.domain.remittancetransaction.service.RecipientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class RecipientController {

    private final RecipientService recipientService;

    @PostMapping("/recipients")
    public ResponseEntity<RecipientResponse> createRecipient(
            @Valid @RequestBody RecipientCreateRequest request
    ) {
        // TODO: 인증 레이어 완성 후 @AuthenticationPrincipal로 실제 유저 ID 주입받도록 수정할 것
        Long mockUserId = 1L;

        RecipientResponse response = recipientService.createRecipient(mockUserId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/recipients")
    public ResponseEntity<List<RecipientResponse>> getRecipients() {
        // TODO: 인증 레이어 완성 후 @AuthenticationPrincipal로 실제 유저 ID 주입받도록 수정할 것
        Long mockUserId = 1L;

        List<RecipientResponse> response = recipientService.getRecipients(mockUserId);
        return ResponseEntity.ok(response);
    }
}