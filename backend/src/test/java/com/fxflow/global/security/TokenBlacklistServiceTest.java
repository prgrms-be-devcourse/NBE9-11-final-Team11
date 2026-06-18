package com.fxflow.global.security;

import com.fxflow.global.security.dto.JwtUserInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("TokenBlacklistService - 로그아웃 토큰 블랙리스트")
class TokenBlacklistServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOperations;
    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private TokenBlacklistService tokenBlacklistService;

    private static final String TOKEN = "dummy-token";
    private static final String JTI = "jti-1234";

    @Nested
    @DisplayName("invalidate - 로그아웃 시 블랙리스트 등록")
    class Invalidate {

        @Test
        @DisplayName("성공: 남은 TTL만큼 Redis에 jti를 등록한다")
        void success() {
            // given
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(jwtTokenProvider.getJwtUserInfo(TOKEN))
                    .willReturn(new JwtUserInfo(1L, "USER", JTI));
            given(jwtTokenProvider.getRemainingTtl(TOKEN))
                    .willReturn(Duration.ofMinutes(10));

            // when
            tokenBlacklistService.invalidate(TOKEN);

            // then
            verify(valueOperations).set(
                    eq("auth:blacklist:" + JTI),
                    eq(Boolean.TRUE),
                    eq(Duration.ofMinutes(10))
            );
        }

        @Test
        @DisplayName("토큰이 null이면 아무 일도 하지 않는다")
        void tokenNull() {
            // when
            tokenBlacklistService.invalidate(null);

            // then
            verify(redisTemplate, never()).opsForValue();
        }

        @Test
        @DisplayName("남은 TTL이 0이면 등록하지 않는다 (이미 만료 직전)")
        void zeroTtl() {
            // given
            given(jwtTokenProvider.getJwtUserInfo(TOKEN))
                    .willReturn(new JwtUserInfo(1L, "USER", JTI));
            given(jwtTokenProvider.getRemainingTtl(TOKEN))
                    .willReturn(Duration.ZERO);

            // when
            tokenBlacklistService.invalidate(TOKEN);

            // then
            verify(redisTemplate, never()).opsForValue();
        }
    }

    @Nested
    @DisplayName("isBlacklisted - 블랙리스트 조회")
    class IsBlacklisted {

        @Test
        @DisplayName("성공: 키가 존재하면 true")
        void found() {
            given(redisTemplate.hasKey("auth:blacklist:" + JTI)).willReturn(true);

            assertThat(tokenBlacklistService.isBlacklisted(JTI)).isTrue();
        }

        @Test
        @DisplayName("성공: 키가 없으면 false")
        void notFound() {
            given(redisTemplate.hasKey("auth:blacklist:" + JTI)).willReturn(false);

            assertThat(tokenBlacklistService.isBlacklisted(JTI)).isFalse();
        }

        @Test
        @DisplayName("jti가 null이면 Redis를 조회하지 않고 false")
        void jtiNull() {
            assertThat(tokenBlacklistService.isBlacklisted(null)).isFalse();

            verify(redisTemplate, never()).hasKey(any());
        }
    }
}