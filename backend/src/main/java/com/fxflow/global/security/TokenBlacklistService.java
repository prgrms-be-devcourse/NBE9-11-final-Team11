package com.fxflow.global.security;

import com.fxflow.global.exception.BusinessException;
import com.fxflow.global.security.dto.JwtUserInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import org.springframework.beans.factory.annotation.Value;

import java.time.Duration;
import java.util.Date;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private static final String REFRESH_BLACKLIST_PREFIX = "auth:refresh-blacklist:";
    private static final String FORCE_LOGOUT_PREFIX = "auth:force-logout:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    /**
     * 로그아웃·탈퇴·비밀번호 변경 시 Refresh Token을 블랙리스트에 등록한다.
     * TTL은 토큰의 남은 만료 시간으로 설정 — 자연 만료 시 Redis에서 자동 삭제.
     */
    public void invalidateRefreshToken(String refreshToken) {
        log.info("[블랙리스트 등록 시도] refreshToken={}", refreshToken == null ? "NULL" : "존재");
        if (refreshToken == null) return;
        try {
            JwtUserInfo info = jwtTokenProvider.getJwtUserInfo(refreshToken);
            Duration ttl = jwtTokenProvider.getRemainingTtl(refreshToken);

            if (info.jti() == null || ttl.isZero() || ttl.isNegative()) return;

            redisTemplate.opsForValue().set(
                    REFRESH_BLACKLIST_PREFIX + info.jti(),
                    Boolean.TRUE,
                    ttl
            );
            log.info("[Refresh 블랙리스트 등록] jti={}, ttl={}s",
                    info.jti(), ttl.getSeconds());
        } catch (BusinessException e) {
            log.debug("[Refresh 블랙리스트 등록 스킵] 이미 만료된 토큰");
        }
    }

    public boolean isRefreshTokenBlacklisted(String jti) {
        if (jti == null) return false;
        return Boolean.TRUE.equals(
                redisTemplate.hasKey(REFRESH_BLACKLIST_PREFIX + jti)
        );
    }

    /**
     * 이미 회전(rotate)되어 폐기된 Refresh Token이 재사용된 경우 호출한다.
     * RT 탈취가 의심되므로 해당 유저가 보유한 모든 AT/RT를 강제로 무효화한다.
     * TTL은 RT 최대 수명과 동일하게 설정 — 그 이후엔 어차피 모든 토큰이 자연 만료됨.
     */
    public void forceLogoutUser(Long userId) {
        redisTemplate.opsForValue().set(
                FORCE_LOGOUT_PREFIX + userId,
                System.currentTimeMillis(),
                Duration.ofMillis(refreshTokenExpiration)
        );
        log.warn("[강제 로그아웃 마커 등록] Refresh Token 재사용 탐지 — userId={}", userId);
    }

    /**
     * 토큰 발급 시각이 강제 로그아웃 마커 시각보다 이전이면 무효 처리 대상이다.
     */
    public boolean isForceLogoutRequired(Long userId, Date tokenIssuedAt) {
        Object marker = redisTemplate.opsForValue().get(FORCE_LOGOUT_PREFIX + userId);
        if (marker == null || tokenIssuedAt == null) return false;

        long forceLogoutAt = Long.parseLong(String.valueOf(marker));
        return tokenIssuedAt.getTime() < forceLogoutAt;
    }
}