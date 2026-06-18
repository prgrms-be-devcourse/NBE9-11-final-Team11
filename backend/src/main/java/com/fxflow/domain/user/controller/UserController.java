package com.fxflow.domain.user.controller;

import com.fxflow.domain.user.dto.request.LoginRequest;
import com.fxflow.domain.user.dto.request.SignupRequest;
import com.fxflow.domain.user.dto.response.LoginResponse;
import com.fxflow.domain.user.dto.response.SignupResponse;
import com.fxflow.domain.user.entity.User;
import com.fxflow.domain.user.service.UserService;
import com.fxflow.global.security.JwtTokenProvider;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 회원가입
     * POST /api/v1/auth/signup
     */
    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(
            @Valid @RequestBody SignupRequest request
    ) {
        SignupResponse response = userService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    /**
     * 로그인
     * POST /api/v1/auth/login
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @RequestBody @Valid LoginRequest loginRequest,
            HttpServletResponse response
    ){
        User user = userService.login(loginRequest);
        ResponseCookie cookie = jwtTokenProvider.generateAccessTokenCookie(user);
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return ResponseEntity.ok(LoginResponse.of(user));
    }
    /**
     * 로그아웃
     * Post /api/v1/auth/logout
     */
    @PostMapping
    public ResponseEntity<Void> logO

}
