package com.fxflow.global.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
/**
 * JWT는 stateless라 토큰 자체를 무효화할 수 없으므로,
 * "이 jti는 더 이상 유효하지 않다"는 사실만 토큰의 남은 만료시간만큼 Redis에 들고 있는다.
 */
public class TokenBlacklistService {
    private static final String BLACKLIST_KEY_PREFIX = "auth:blacklist:";

    private final RedisTemplate<String,Object> redisTemplate;
    private final JwtTokenProvider jwtTokenProvider;
    /** 로그아웃 시 호출 — 토큰의 jti를 남은 만료시간만큼 블랙리스트에 등록한다. */
    public void invalidate(String token){
        if(token==null){
            return;
        }
        String jti = jwtTokenProvider.getJwtUserInfo(token).jti();
        Duration remainingTtl = jwtTokenProvider.getRemainingTtl(token);
        if(jti==null||remainingTtl.isZero()||remainingTtl.isNegative()){
            return;
        }
        redisTemplate.opsForValue().set(BLACKLIST_KEY_PREFIX + jti, Boolean.TRUE, remainingTtl);
        log.info("[토큰 블랙리스트 등록] jti={}, ttlSeconds={}", jti, remainingTtl.getSeconds());
    }
    /** 매 요청마다 호출 — 로그아웃 처리된 토큰인지 확인한다. */
    public boolean isBlacklisted(String jti) {
        if (jti == null) {
            return false;
        }
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_KEY_PREFIX + jti));
    }
}
