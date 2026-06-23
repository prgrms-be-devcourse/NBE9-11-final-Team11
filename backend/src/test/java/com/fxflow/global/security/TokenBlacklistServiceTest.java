package com.fxflow.global.security;

import com.fxflow.global.exception.BusinessException;
import com.fxflow.global.security.dto.JwtUserInfo;
import com.fxflow.global.security.errorcode.AuthErrorCode;
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
@DisplayName("TokenBlacklistService - Refresh Token 블랙리스트")
class TokenBlacklistServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOperations;
    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private TokenBlacklistService tokenBlacklistService;

    private static final String REFRESH_TOKEN = "dummy-refresh-token";
    private static final String JTI = "jti-1234";
    // Access Token 블랙리스트 제거 → Refresh Token 전용 prefix
    private static final String REFRESH_BLACKLIST_PREFIX = "auth:refresh-blacklist:";

    @Nested
    @DisplayName("invalidateRefreshToken - 로그아웃 시 Refresh Token 블랙리스트 등록")
    class InvalidateRefreshToken {

        @Test
        @DisplayName("성공: 남은 TTL만큼 Redis에 jti를 등록한다")
        void success() {
            // given
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(jwtTokenProvider.getJwtUserInfo(REFRESH_TOKEN))
                    .willReturn(new JwtUserInfo(1L, null, JTI)); // Refresh Token은 role 없음
            given(jwtTokenProvider.getRemainingTtl(REFRESH_TOKEN))
                    .willReturn(Duration.ofDays(7));

            // when
            tokenBlacklistService.invalidateRefreshToken(REFRESH_TOKEN);

            // then
            verify(valueOperations).set(
                    eq(REFRESH_BLACKLIST_PREFIX + JTI),
                    eq(Boolean.TRUE),
                    eq(Duration.ofDays(7))
            );
        }

        @Test
        @DisplayName("Refresh Token이 null이면 아무 일도 하지 않는다")
        void tokenNull() {
            // when
            tokenBlacklistService.invalidateRefreshToken(null);

            // then
            verify(redisTemplate, never()).opsForValue();
        }

        @Test
        @DisplayName("남은 TTL이 0이면 등록하지 않는다 (이미 만료)")
        void zeroTtl() {
            // given
            given(jwtTokenProvider.getJwtUserInfo(REFRESH_TOKEN))
                    .willReturn(new JwtUserInfo(1L, null, JTI));
            given(jwtTokenProvider.getRemainingTtl(REFRESH_TOKEN))
                    .willReturn(Duration.ZERO);

            // when
            tokenBlacklistService.invalidateRefreshToken(REFRESH_TOKEN);

            // then
            verify(redisTemplate, never()).opsForValue();
        }

        @Test
        @DisplayName("만료된 Refresh Token이면 등록하지 않는다 (BusinessException 처리)")
        void expiredToken() {
            // given - 만료된 토큰은 getJwtUserInfo에서 BusinessException 발생
            given(jwtTokenProvider.getJwtUserInfo(REFRESH_TOKEN))
                    .willThrow(new BusinessException(AuthErrorCode.UNAUTHORIZED));

            // when - 예외가 밖으로 전파되지 않아야 함
            tokenBlacklistService.invalidateRefreshToken(REFRESH_TOKEN);

            // then
            verify(redisTemplate, never()).opsForValue();
        }

        @Test
        @DisplayName("jti가 null이면 등록하지 않는다")
        void jtiNull() {
            // given
            given(jwtTokenProvider.getJwtUserInfo(REFRESH_TOKEN))
                    .willReturn(new JwtUserInfo(1L, null, null)); // jti null
            given(jwtTokenProvider.getRemainingTtl(REFRESH_TOKEN))
                    .willReturn(Duration.ofDays(7));

            // when
            tokenBlacklistService.invalidateRefreshToken(REFRESH_TOKEN);

            // then
            verify(redisTemplate, never()).opsForValue();
        }
    }

    @Nested
    @DisplayName("isRefreshTokenBlacklisted - Refresh Token 블랙리스트 조회")
    class IsRefreshTokenBlacklisted {

        @Test
        @DisplayName("성공: 키가 존재하면 true (로그아웃된 토큰)")
        void found() {
            given(redisTemplate.hasKey(REFRESH_BLACKLIST_PREFIX + JTI)).willReturn(true);

            assertThat(tokenBlacklistService.isRefreshTokenBlacklisted(JTI)).isTrue();
        }

        @Test
        @DisplayName("성공: 키가 없으면 false (유효한 토큰)")
        void notFound() {
            given(redisTemplate.hasKey(REFRESH_BLACKLIST_PREFIX + JTI)).willReturn(false);

            assertThat(tokenBlacklistService.isRefreshTokenBlacklisted(JTI)).isFalse();
        }

        @Test
        @DisplayName("jti가 null이면 Redis를 조회하지 않고 false")
        void jtiNull() {
            assertThat(tokenBlacklistService.isRefreshTokenBlacklisted(null)).isFalse();

            verify(redisTemplate, never()).hasKey(any());
        }
    }
}
