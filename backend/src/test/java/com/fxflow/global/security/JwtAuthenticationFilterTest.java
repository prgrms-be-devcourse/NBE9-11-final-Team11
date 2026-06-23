package com.fxflow.global.security;

import com.fxflow.global.exception.BusinessException;
import com.fxflow.global.security.dto.JwtUserInfo;
import com.fxflow.global.security.errorcode.AuthErrorCode;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthenticationFilter - Access Token 검증")
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;
    // Access Token 블랙리스트 제거 → TokenBlacklistService 의존성 없음

    private JwtAuthenticationFilter filter;

    private static final String TOKEN = "valid-token";
    private static final String JTI = "jti-1";

    @BeforeEach
    void setUp() {
        // TokenBlacklistService 제거된 생성자
        filter = new JwtAuthenticationFilter(jwtTokenProvider);
    }

    @AfterEach
    void clearContext() {
        // 다음 테스트로 인증 정보가 새지 않도록 정리
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest requestWithCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("accessToken", TOKEN));
        return request;
    }

    @Test
    @DisplayName("정상 토큰이면 SecurityContext에 인증 정보를 저장한다")
    void validToken_setsAuthentication() throws Exception {
        // given
        given(jwtTokenProvider.getJwtUserInfo(TOKEN))
                .willReturn(new JwtUserInfo(1L, "USER", JTI));

        // when
        filter.doFilter(requestWithCookie(), new MockHttpServletResponse(), new MockFilterChain());

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("토큰 파싱/서명 검증에 실패하면 인증하지 않는다")
    void invalidToken_doesNotAuthenticate() throws Exception {
        // given
        given(jwtTokenProvider.getJwtUserInfo(TOKEN))
                .willThrow(new BusinessException(AuthErrorCode.UNAUTHORIZED));

        // when
        filter.doFilter(requestWithCookie(), new MockHttpServletResponse(), new MockFilterChain());

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("만료된 토큰이면 인증하지 않는다")
    void expiredToken_doesNotAuthenticate() throws Exception {
        // given - 만료된 토큰도 동일하게 UNAUTHORIZED 예외 발생
        given(jwtTokenProvider.getJwtUserInfo(TOKEN))
                .willThrow(new BusinessException(AuthErrorCode.UNAUTHORIZED));

        // when
        filter.doFilter(requestWithCookie(), new MockHttpServletResponse(), new MockFilterChain());

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("쿠키가 없으면 토큰 파싱을 시도하지 않고 그냥 통과시킨다")
    void noCookie_skipsAuthentication() throws Exception {
        // given - 쿠키 없는 요청
        MockHttpServletRequest request = new MockHttpServletRequest();

        // when
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtTokenProvider, never()).getJwtUserInfo(any());
    }

    @Test
    @DisplayName("ADMIN 역할 토큰이면 ROLE_ADMIN 권한이 부여된다")
    void adminToken_setsAdminRole() throws Exception {
        // given
        given(jwtTokenProvider.getJwtUserInfo(TOKEN))
                .willReturn(new JwtUserInfo(1L, "ADMIN", JTI));

        // when
        filter.doFilter(requestWithCookie(), new MockHttpServletResponse(), new MockFilterChain());

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
