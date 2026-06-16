package com.fxflow.global.security;

import com.fxflow.domain.user.entity.User;
import com.fxflow.global.exception.BusinessException;
import com.fxflow.global.security.errorcode.AuthErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long accessTokenExpiration;
    private final boolean cookieSecure;
    private final String cookieSameSite;
    private final int cookieMaxAge;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration}") long accessTokenExpiration,
            @Value("${cookie.secure}") boolean cookieSecure,
            @Value("${cookie.same-site}") String cookieSameSite,
            @Value("${cookie.max-age}") int cookieMaxAge
    ) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiration = accessTokenExpiration;
        this.cookieSecure = cookieSecure;
        this.cookieSameSite = cookieSameSite;
        this.cookieMaxAge = cookieMaxAge;
    }

    /**
     * 로그인 성공 시 Access Token 발급
     * subject: userId
     * claim: role
     */
    public String generateAccessToken(User user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTokenExpiration);

        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("role", user.getRole().name())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    /**
     * Access Token을 HttpOnly 쿠키로 생성
     * HttpOnly: JS 접근 불가 (XSS 방어)
     * SameSite=Strict: 크로스 도메인 차단 (CSRF 방어)
     */
    public ResponseCookie generateAccessTokenCookie(User user) {
        String token = generateAccessToken(user);

        return ResponseCookie.from("accessToken", token)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path("/")
                .maxAge(cookieMaxAge)
                .build();
    }

    /**
     * 로그아웃 시 쿠키 삭제용 빈 쿠키 생성
     * maxAge=0으로 즉시 만료
     */
    public ResponseCookie deleteAccessTokenCookie() {
        return ResponseCookie.from("accessToken", "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path("/")
                .maxAge(0)
                .build();
    }

    /**
     * 토큰 유효성 검증
     * 실패 이유는 로그에만 기록하고 클라이언트에는 노출하지 않음
     */
    public void validateToken(String token) {
        try {
            parseClaims(token);
        } catch (ExpiredJwtException e) {
            log.warn("[JWT] 토큰 만료 — 인증 실패");
            throw new BusinessException(AuthErrorCode.UNAUTHORIZED);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("[JWT] 토큰 검증 실패: {}", e.getMessage());
            throw new BusinessException(AuthErrorCode.UNAUTHORIZED);
        }
    }

    /**
     * 토큰에서 userId 추출
     */
    public Long getUserId(String token) {
        return Long.parseLong(parseClaims(token).getSubject());
    }

    /**
     * 토큰에서 role 추출
     */
    public String getRole(String token) {
        return parseClaims(token).get("role", String.class);
    }

    /**
     * 토큰 만료 시간을 LocalDateTime으로 반환 (KST 기준)
     */
    public LocalDateTime getExpirationDateTime(String token) {
        return LocalDateTime.ofInstant(
                parseClaims(token).getExpiration().toInstant(),
                ZoneId.of("Asia/Seoul")
        );
    }

    /**
     * JWT 파싱 — 서명 검증 포함
     */
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}