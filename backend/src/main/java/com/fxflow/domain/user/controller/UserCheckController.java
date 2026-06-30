package com.fxflow.domain.user.controller;

import com.fxflow.domain.user.dto.response.UserCheckResponse;
import com.fxflow.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/check")
@RequiredArgsConstructor
public class UserCheckController {
    private final UserService userService;
    @GetMapping
    public ResponseEntity<UserCheckResponse> checkUser(
            @AuthenticationPrincipal Long currentUserId,
            @RequestParam String email
    ) {
        UserCheckResponse res = userService.checkRecipient(currentUserId, email);
        return ResponseEntity.ok(res);
    }

}
