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
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthenticationFilter - 토큰/블랙리스트 검증")
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private TokenBlacklistService tokenBlacklistService;

    private JwtAuthenticationFilter filter;

    private static final String TOKEN = "valid-token";
    private static final String JTI = "jti-1";

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtTokenProvider, tokenBlacklistService);
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
        given(tokenBlacklistService.isBlacklisted(JTI)).willReturn(false);

        // when
        filter.doFilter(requestWithCookie(), new MockHttpServletResponse(), new MockFilterChain());

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("블랙리스트에 등록된 토큰이면 인증하지 않는다")
    void blacklistedToken_doesNotAuthenticate() throws Exception {
        // given - 로그아웃 처리된 토큰 상황
        given(jwtTokenProvider.getJwtUserInfo(TOKEN))
                .willReturn(new JwtUserInfo(1L, "USER", JTI));
        given(tokenBlacklistService.isBlacklisted(JTI)).willReturn(true);

        // when
        filter.doFilter(requestWithCookie(), new MockHttpServletResponse(), new MockFilterChain());

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
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
    @DisplayName("쿠키가 없으면 토큰 파싱을 시도하지 않고 그냥 통과시킨다")
    void noCookie_skipsAuthentication() throws Exception {
        // given - 쿠키 없는 요청
        MockHttpServletRequest request = new MockHttpServletRequest();

        // when
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}