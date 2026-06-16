package com.fxflow.global.security;

import com.fxflow.domain.user.entity.User;
import com.fxflow.global.security.errorcode.AuthErrorCode;
import com.fxflow.global.exception.BusinessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

@Slf4j
@Component
public class JwtTokenProvider {

    private static final String TOKEN_TYPE_CLAIM = "type";
    private static final String ACCESS_TOKEN_TYPE = "access";

    private final SecretKey secretKey;
    private final long accessTokenExpiration;

    //생성자로 초기화
    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration}") long accessTokenExpiration
    ) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiration = accessTokenExpiration;
    }

    /**
     * 로그인 성공 시 Access Token 발급
     * subject: userId
     * claim: role, type
     * email은 보안상 JWT에 포함하지 않음 — 필요 시 userId로 DB 조회
     */
    public String generateAccessToken(User user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTokenExpiration);

        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim(TOKEN_TYPE_CLAIM, ACCESS_TOKEN_TYPE)
                .claim("role", user.getRole().name())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    /**
     * 토큰 유효성 검증
     * 만료/위조/형식 오류 모두 동일하게 UNAUTHORIZED 반환
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
     * subject에 저장된 userId를 Long으로 변환
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
     * 서명 불일치, 만료 등 예외 발생
     */
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}