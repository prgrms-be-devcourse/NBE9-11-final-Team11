package com.fxflow.global.security;

import com.fxflow.global.exception.BusinessException;
import com.fxflow.global.security.dto.JwtUserInfo;
import com.fxflow.global.security.errorcode.AuthErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Date;

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
    private static final String FORCE_LOGOUT_PREFIX = "auth:force-logout:";
    private static final long REFRESH_TOKEN_EXPIRATION = Duration.ofDays(7).toMillis();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(tokenBlacklistService, "refreshTokenExpiration", REFRESH_TOKEN_EXPIRATION);
    }

    @Nested
    @DisplayName("invalidateRefreshToken - 로그아웃 시 Refresh Token 블랙리스트 등록")
    class InvalidateRefreshToken {

        @Test
        @DisplayName("성공: 남은 TTL만큼 Redis에 jti를 등록한다")
        void success() {
            // given
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(jwtTokenProvider.getJwtUserInfo(REFRESH_TOKEN))
                    .willReturn(new JwtUserInfo(1L, null, JTI, new Date())); // Refresh Token은 role 없음
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
                    .willReturn(new JwtUserInfo(1L, null, JTI, new Date()));
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
                    .willReturn(new JwtUserInfo(1L, null, null, new Date())); // jti null
            given(jwtTokenProvider.getRemainingTtl(REFRESH_TOKEN))
                    .willReturn(Duration.ofDays(7));

            // when
            tokenBlacklistService.invalidateRefreshToken(REFRESH_TOKEN);

            // then
            verify(redisTemplate, never()).opsForValue();
        }
    }

    @Nested
    @DisplayName("tryRotateRefreshToken - RT 회전 권한의 원자적 점유")
    class TryRotateRefreshToken {

        @Test
        @DisplayName("성공: 처음 회전 시도면 점유에 성공하고 true를 반환한다")
        void success() {
            // given
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(jwtTokenProvider.getJwtUserInfo(REFRESH_TOKEN))
                    .willReturn(new JwtUserInfo(1L, null, JTI, new Date()));
            given(jwtTokenProvider.getRemainingTtl(REFRESH_TOKEN))
                    .willReturn(Duration.ofDays(7));
            given(valueOperations.setIfAbsent(
                    REFRESH_BLACKLIST_PREFIX + JTI, Boolean.TRUE, Duration.ofDays(7)
            )).willReturn(true);

            // when & then
            assertThat(tokenBlacklistService.tryRotateRefreshToken(REFRESH_TOKEN)).isTrue();
        }

        @Test
        @DisplayName("실패: 이미 다른 요청이 같은 RT로 먼저 회전시켰으면 false를 반환한다 (재사용 감지)")
        void alreadyRotated() {
            // given - 동시 요청 race를 흉내: 동일 jti에 대한 setIfAbsent가 false 반환
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(jwtTokenProvider.getJwtUserInfo(REFRESH_TOKEN))
                    .willReturn(new JwtUserInfo(1L, null, JTI, new Date()));
            given(jwtTokenProvider.getRemainingTtl(REFRESH_TOKEN))
                    .willReturn(Duration.ofDays(7));
            given(valueOperations.setIfAbsent(
                    REFRESH_BLACKLIST_PREFIX + JTI, Boolean.TRUE, Duration.ofDays(7)
            )).willReturn(false);

            // when & then
            assertThat(tokenBlacklistService.tryRotateRefreshToken(REFRESH_TOKEN)).isFalse();
        }

        @Test
        @DisplayName("Refresh Token이 null이면 점유를 시도하지 않고 false")
        void tokenNull() {
            assertThat(tokenBlacklistService.tryRotateRefreshToken(null)).isFalse();

            verify(redisTemplate, never()).opsForValue();
        }

        @Test
        @DisplayName("남은 TTL이 0이면 점유를 시도하지 않고 false (이미 만료)")
        void zeroTtl() {
            given(jwtTokenProvider.getJwtUserInfo(REFRESH_TOKEN))
                    .willReturn(new JwtUserInfo(1L, null, JTI, new Date()));
            given(jwtTokenProvider.getRemainingTtl(REFRESH_TOKEN))
                    .willReturn(Duration.ZERO);

            assertThat(tokenBlacklistService.tryRotateRefreshToken(REFRESH_TOKEN)).isFalse();

            verify(redisTemplate, never()).opsForValue();
        }

        @Test
        @DisplayName("만료된 Refresh Token이면 false (BusinessException 처리)")
        void expiredToken() {
            given(jwtTokenProvider.getJwtUserInfo(REFRESH_TOKEN))
                    .willThrow(new BusinessException(AuthErrorCode.UNAUTHORIZED));

            assertThat(tokenBlacklistService.tryRotateRefreshToken(REFRESH_TOKEN)).isFalse();

            verify(redisTemplate, never()).opsForValue();
        }

        @Test
        @DisplayName("jti가 null이면 점유를 시도하지 않고 false")
        void jtiNull() {
            given(jwtTokenProvider.getJwtUserInfo(REFRESH_TOKEN))
                    .willReturn(new JwtUserInfo(1L, null, null, new Date()));
            given(jwtTokenProvider.getRemainingTtl(REFRESH_TOKEN))
                    .willReturn(Duration.ofDays(7));

            assertThat(tokenBlacklistService.tryRotateRefreshToken(REFRESH_TOKEN)).isFalse();

            verify(redisTemplate, never()).opsForValue();
        }
    }

    @Nested
    @DisplayName("forceLogoutUser - RT 재사용 탐지 시 강제 로그아웃 마커 등록")
    class ForceLogoutUser {

        @Test
        @DisplayName("성공: RT 만료 기간만큼 TTL로 강제 로그아웃 마커를 등록한다")
        void success() {
            given(redisTemplate.opsForValue()).willReturn(valueOperations);

            tokenBlacklistService.forceLogoutUser(1L);

            verify(valueOperations).set(
                    eq(FORCE_LOGOUT_PREFIX + 1L),
                    any(Long.class),
                    eq(Duration.ofMillis(REFRESH_TOKEN_EXPIRATION))
            );
        }
    }

    @Nested
    @DisplayName("isForceLogoutRequired - 강제 로그아웃 마커 검사")
    class IsForceLogoutRequired {

        @Test
        @DisplayName("마커가 없으면 false")
        void noMarker() {
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get(FORCE_LOGOUT_PREFIX + 1L)).willReturn(null);

            assertThat(tokenBlacklistService.isForceLogoutRequired(1L, new Date())).isFalse();
        }

        @Test
        @DisplayName("토큰 발급 시각이 마커 시각보다 이전이면 true (강제 로그아웃 대상)")
        void issuedBeforeMarker() {
            long markerTime = System.currentTimeMillis();
            Date issuedAt = new Date(markerTime - 1000);

            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get(FORCE_LOGOUT_PREFIX + 1L)).willReturn(markerTime);

            assertThat(tokenBlacklistService.isForceLogoutRequired(1L, issuedAt)).isTrue();
        }

        @Test
        @DisplayName("토큰 발급 시각이 마커 시각보다 이후면 false (마커 등록 후 새로 발급된 토큰)")
        void issuedAfterMarker() {
            long markerTime = System.currentTimeMillis();
            Date issuedAt = new Date(markerTime + 1000);

            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get(FORCE_LOGOUT_PREFIX + 1L)).willReturn(markerTime);

            assertThat(tokenBlacklistService.isForceLogoutRequired(1L, issuedAt)).isFalse();
        }
    }
}
