package com.fxflow.global.security;

import com.fxflow.global.exception.BusinessException;
import com.fxflow.global.security.dto.JwtUserInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private static final String REFRESH_BLACKLIST_PREFIX = "auth:refresh-blacklist:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final JwtTokenProvider jwtTokenProvider;

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
}