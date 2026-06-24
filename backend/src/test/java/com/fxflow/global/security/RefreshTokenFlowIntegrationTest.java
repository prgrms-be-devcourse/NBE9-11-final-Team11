package com.fxflow.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fxflow.domain.user.dto.request.LoginRequest;
import com.fxflow.domain.user.dto.request.SignupRequest;
import com.fxflow.support.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
@DisplayName("Refresh Token 회전/재사용 탐지 통합 테스트")
class RefreshTokenFlowIntegrationTest extends AbstractIntegrationTest {

    private static final String EMAIL = "refresh-flow-user@example.com";
    private static final String NAME = "리프레시테스트";
    private static final String PASSWORD = "Abcd1234!";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        truncateAll();
    }

    @AfterEach
    void cleanUp() {
        truncateAll();
    }

    @Test
    @DisplayName("정상 회전: refreshToken으로 재발급받으면 새 accessToken/refreshToken 쿠키가 발급된다")
    void refresh_success_issuesNewTokens() throws Exception {
        Cookie refreshTokenCookie = signupAndLogin().getCookie("refreshToken");

        MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshTokenCookie))
                .andExpect(status().isOk())
                .andReturn();

        Cookie newAccessToken = result.getResponse().getCookie("accessToken");
        Cookie newRefreshToken = result.getResponse().getCookie("refreshToken");
        assertThat(newAccessToken).isNotNull();
        assertThat(newRefreshToken).isNotNull();
        assertThat(newRefreshToken.getValue()).isNotEqualTo(refreshTokenCookie.getValue());
    }

    @Test
    @DisplayName("재사용 탐지: 이미 회전에 사용된 refreshToken으로 다시 재발급을 시도하면 차단된다")
    void refresh_fail_reusedRefreshTokenIsRejected() throws Exception {
        Cookie refreshTokenCookie = signupAndLogin().getCookie("refreshToken");

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(refreshTokenCookie))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(refreshTokenCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
    }

    @Test
    @DisplayName("강제 로그아웃 마커 등록: RT 재사용이 감지되면 해당 사용자에게 강제 로그아웃 마커가 등록되어 재사용된 토큰은 계속 차단된다")
    void refresh_fail_reuseKeepsBeingRejectedAfterForceLogoutMarker() throws Exception {
        Cookie firstRefreshToken = signupAndLogin().getCookie("refreshToken");

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(firstRefreshToken))
                .andExpect(status().isOk());

        // 탈취된 첫 번째 RT(이미 회전되어 사용 불가)를 재사용 시도 → 재사용 탐지 → 강제 로그아웃 마커 등록
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(firstRefreshToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));

        // 같은 토큰으로 다시 시도해도 여전히 차단된다 (블랙리스트에 영구 등록된 상태)
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(firstRefreshToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
    }

    @Test
    @DisplayName("로그아웃 후 재사용 차단: 로그아웃 시점의 refreshToken으로는 재발급을 받을 수 없다")
    void refresh_fail_afterLogout() throws Exception {
        MockHttpServletResponse loginResponse = signupAndLogin();
        Cookie accessTokenCookie = loginResponse.getCookie("accessToken");
        Cookie refreshTokenCookie = loginResponse.getCookie("refreshToken");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(accessTokenCookie, refreshTokenCookie))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(refreshTokenCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
    }

    @Test
    @DisplayName("동시성: 같은 refreshToken으로 동시에 재발급을 시도해도 정확히 한 건만 성공한다")
    void refresh_concurrent_onlyOneRotationSucceeds() throws Exception {
        Cookie refreshTokenCookie = signupAndLogin().getCookie("refreshToken");

        List<Integer> statusCodes = runRefreshConcurrently(refreshTokenCookie);

        assertThat(statusCodes).containsExactlyInAnyOrder(200, 401);
    }

    @Test
    @DisplayName("만료/위조 토큰 차단: accessToken 쿠키 없이 인증이 필요한 API를 호출하면 401을 반환한다")
    void protectedApi_fail_withoutAccessToken() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/mock-account"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("위조 토큰 차단: 서명이 다른 accessToken 쿠키로는 인증이 필요한 API를 호출할 수 없다")
    void protectedApi_fail_withTamperedAccessToken() throws Exception {
        Cookie tamperedCookie = new Cookie("accessToken", "tampered.invalid.token");

        mockMvc.perform(get("/api/v1/users/me/mock-account")
                        .cookie(tamperedCookie))
                .andExpect(status().isUnauthorized());
    }

    private List<Integer> runRefreshConcurrently(Cookie refreshTokenCookie) throws Exception {
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        try {
            Future<Integer> firstFuture = executorService.submit(
                    () -> refreshAfterStartSignal(refreshTokenCookie, readyLatch, startLatch));
            Future<Integer> secondFuture = executorService.submit(
                    () -> refreshAfterStartSignal(refreshTokenCookie, readyLatch, startLatch));

            assertThat(readyLatch.await(3, TimeUnit.SECONDS)).isTrue();
            startLatch.countDown();

            return List.of(firstFuture.get(10, TimeUnit.SECONDS), secondFuture.get(10, TimeUnit.SECONDS));
        } finally {
            executorService.shutdownNow();
        }
    }

    private int refreshAfterStartSignal(Cookie refreshTokenCookie, CountDownLatch readyLatch, CountDownLatch startLatch) {
        readyLatch.countDown();
        try {
            startLatch.await();
            return mockMvc.perform(post("/api/v1/auth/refresh").cookie(refreshTokenCookie))
                    .andReturn()
                    .getResponse()
                    .getStatus();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private MockHttpServletResponse signupAndLogin() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(new SignupRequest(NAME, EMAIL, PASSWORD))));

        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LoginRequest(EMAIL, PASSWORD))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse();
    }

    private void truncateAll() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    users
                RESTART IDENTITY CASCADE
                """);
    }
}
