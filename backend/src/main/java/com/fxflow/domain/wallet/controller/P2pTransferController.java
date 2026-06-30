package com.fxflow.domain.wallet.controller;

import com.fxflow.domain.wallet.dto.request.P2pTransferRequest;
import com.fxflow.domain.wallet.dto.response.P2pTransferResponse;
import com.fxflow.domain.wallet.service.P2pTransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wallets")
@RequiredArgsConstructor
public class P2pTransferController {

    private final P2pTransferService p2pTransferService;

    @PostMapping("/transfer")
    public ResponseEntity<P2pTransferResponse> transfer(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody P2pTransferRequest request
    ) {
        P2pTransferResponse res = p2pTransferService.transfer(userId, request);
        return ResponseEntity.ok(res);
    }

}