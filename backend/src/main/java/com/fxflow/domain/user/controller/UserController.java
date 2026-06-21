package com.fxflow.domain.user.controller;

import com.fxflow.domain.user.dto.request.ChangePasswordRequest;
import com.fxflow.domain.user.dto.request.LoginRequest;
import com.fxflow.domain.user.dto.request.SignupRequest;
import com.fxflow.domain.user.dto.request.WithdrawRequest;
import com.fxflow.domain.user.dto.response.LoginResponse;
import com.fxflow.domain.user.dto.response.SignupResponse;
import com.fxflow.domain.user.dto.response.WithdrawUserResponse;
import com.fxflow.domain.user.entity.User;
import com.fxflow.domain.user.service.UserService;
import com.fxflow.global.security.CookieTokenExtractor;
import com.fxflow.global.security.JwtTokenProvider;
import com.fxflow.global.security.TokenBlacklistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;


@Tag(name = "회원 인증", description = "회원가입, 로그인, 로그아웃, 회원 탈퇴, 비밀번호 변경 API")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;
    private final TokenBlacklistService tokenBlacklistService;


    @Operation(
            summary = "회원가입",
            description = "이름, 이메일, 비밀번호로 신규 회원을 생성합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "회원가입 성공"),
            @ApiResponse(responseCode = "400", description = "비밀번호 정책 위반"),
            @ApiResponse(responseCode = "409", description = "이미 사용 중인 이메일")
    })
    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(
            @Valid @RequestBody SignupRequest request
    ) {
        SignupResponse response = userService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }


    @Operation(
            summary = "로그인",
            description = "이메일과 비밀번호로 로그인합니다. 성공 시 accessToken이 HttpOnly 쿠키로 발급됩니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그인 성공"),
            @ApiResponse(responseCode = "401", description = "이메일 또는 비밀번호 불일치")
    })
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

    @Operation(
            summary = "로그아웃",
            description = "현재 토큰을 블랙리스트에 등록해 즉시 무효화하고, accessToken 쿠키를 삭제합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그아웃 성공"),
            @ApiResponse(responseCode = "401", description = "토큰 없음 또는 만료/유효하지 않음")
    })
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String token = CookieTokenExtractor.extract(request);
        tokenBlacklistService.invalidate(token);

        ResponseCookie cookie = jwtTokenProvider.deleteAccessTokenCookie();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return ResponseEntity.ok().build();
    }


    @Operation(
            summary = "회원 탈퇴",
            description = "비밀번호 재확인 후 회원 탈퇴를 처리합니다. 잔액이 남아있거나 진행 중인 해외송금 거래가 있으면 탈퇴가 차단됩니다. 탈퇴 처리 후 현재 토큰도 함께 무효화됩니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "탈퇴 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패 또는 비밀번호 불일치"),
            @ApiResponse(responseCode = "409", description = "잔액 또는 진행 중 거래 존재로 탈퇴 차단")
    })
    @DeleteMapping("/me")
    public ResponseEntity<WithdrawUserResponse> withdraw(
            @AuthenticationPrincipal Long id,
            @Valid @RequestBody WithdrawRequest withdrawRequest,
            HttpServletRequest httpRequest,
            HttpServletResponse response
    ) {
        WithdrawUserResponse withdrawUserResponse = userService.withDrawn(id, withdrawRequest.password());
        String token = CookieTokenExtractor.extract(httpRequest);
        if (token != null) {
            tokenBlacklistService.invalidate(token);
        }
        ResponseCookie cookie = jwtTokenProvider.deleteAccessTokenCookie();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return ResponseEntity.ok(withdrawUserResponse);
    }


    @Operation(
            summary = "비밀번호 변경",
            description = "현재 비밀번호를 확인한 후 새 비밀번호로 변경합니다. 보안을 위해 변경 성공 시 현재 세션(토큰)도 함께 무효화되며, 재로그인이 필요합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "비밀번호 변경 성공"),
            @ApiResponse(responseCode = "400", description = "새 비밀번호 정책 위반 또는 기존 비밀번호와 동일"),
            @ApiResponse(responseCode = "401", description = "현재 비밀번호 불일치")
    })
    @PatchMapping("/me/password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response
    ) {
        userService.changePassword(userId, request.currentPassword(), request.newPassword());

        String token = CookieTokenExtractor.extract(httpRequest);
        if (token != null) {
            tokenBlacklistService.invalidate(token);
        }
        ResponseCookie cookie = jwtTokenProvider.deleteAccessTokenCookie();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return ResponseEntity.noContent().build();
    }



}
