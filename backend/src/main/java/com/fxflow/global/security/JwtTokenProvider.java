package com.fxflow.global.security;

import com.fxflow.domain.user.entity.User;
import com.fxflow.global.exception.BusinessException;
import com.fxflow.global.security.dto.JwtUserInfo;
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
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Component
public class JwtTokenProvider {

    private static final String ROLE_CLAIM = "role";
    private static final String COOKIE_NAME = "accessToken";
    private static final String TIME_ZONE = "Asia/Seoul";

    private final SecretKey secretKey;
    private final long accessTokenExpiration;
    private final boolean cookieSecure;
    private final String cookieSameSite;
    private final int cookieMaxAge;
    private final long refreshTokenExpiration;
    private final int cookieRefreshMaxAge;


    /**
     * 생성자 방식으로 초기화
     * final 필드 보장 및 불변성 확보
     */
    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration}") long accessTokenExpiration,
            @Value("${jwt.refresh-token-expiration}") long refreshTokenExpiration,
            @Value("${cookie.secure}") boolean cookieSecure,
            @Value("${cookie.same-site}") String cookieSameSite,
            @Value("${cookie.max-age}") int cookieMaxAge,
            @Value("${cookie.refresh-max-age}") int cookieRefreshMaxAge

    ) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
        this.cookieSecure = cookieSecure;
        this.cookieSameSite = cookieSameSite;
        this.cookieMaxAge = cookieMaxAge;
        this.cookieRefreshMaxAge = cookieRefreshMaxAge;
    }

    /**
     * 로그인 성공 시 Access Token 발급
     * subject: userId
     * claim: role
     * email은 보안상 JWT에 포함하지 않음 — 필요 시 userId로 DB 조회
     */
    public String generateAccessToken(User user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTokenExpiration);

        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .id(UUID.randomUUID().toString()) // 토큰 고유 식별자
                .claim(ROLE_CLAIM, user.getRole().name())
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

        return ResponseCookie.from(COOKIE_NAME, token)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path("/")
                .maxAge(cookieMaxAge)
                .build();
    }

    // Refresh Token 생성 메서드 추가
    public String generateRefreshToken(User user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + refreshTokenExpiration);

        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    // Refresh Token 쿠키 생성 메서드 추가
    public ResponseCookie generateRefreshTokenCookie(User user) {
        String token = generateRefreshToken(user);
        return ResponseCookie.from("refreshToken", token)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path("/api/v1/auth")
                .maxAge(cookieRefreshMaxAge)
                .build();
    }

    // Refresh Token 삭제 쿠키
    public ResponseCookie deleteRefreshTokenCookie() {
        return ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path("/api/v1/auth")
                .maxAge(0)
                .build();
    }



    /**
     * 로그아웃 시 쿠키 삭제용 빈 쿠키 생성
     * maxAge=0으로 즉시 만료
     */
    public ResponseCookie deleteAccessTokenCookie() {
        return ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path("/")
                .maxAge(0)
                .build();
    }


    public JwtUserInfo getJwtUserInfo(String token) {
        Claims claims = parseClaims(token); // 여기서 검증과 파싱이 1번만 발생

        Long userId = Long.parseLong(claims.getSubject());
        String role = claims.get(ROLE_CLAIM, String.class);
        String jti = claims.getId();

        return new JwtUserInfo(userId, role,jti);
    }

    /**
     * 토큰 만료 시간을 LocalDateTime으로 반환 (KST 기준)
     */
    public LocalDateTime getExpirationDateTime(String token) {
        return LocalDateTime.ofInstant(
                parseClaims(token).getExpiration().toInstant(),
                ZoneId.of(TIME_ZONE)
        );
    }
    /**
     * 토큰의 남은 유효시간을 계산한다. 블랙리스트 TTL로 사용 —
     * 자연 만료 시점에 Redis에서도 같이 사라지게 한다. 이미 만료됐으면 ZERO.
     */
    public Duration getRemainingTtl(String token) {
        Date expiration = parseClaims(token).getExpiration();
        long remainingMillis = expiration.getTime() - System.currentTimeMillis();
        return remainingMillis > 0 ? Duration.ofMillis(remainingMillis) : Duration.ZERO;
    }

    /**
     * JWT 파싱 및 예외 처리 공통화
     * 서명 검증 포함
     * 실패 이유는 로그에만 기록하고 클라이언트에는 UNAUTHORIZED만 반환
     */
    private Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            log.warn("[JWT] 토큰 만료 — 인증 실패: {}", e.getMessage());
            throw new BusinessException(AuthErrorCode.UNAUTHORIZED);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("[JWT] 토큰 검증 실패: {}", e.getMessage());
            throw new BusinessException(AuthErrorCode.UNAUTHORIZED);
        }
    }
}