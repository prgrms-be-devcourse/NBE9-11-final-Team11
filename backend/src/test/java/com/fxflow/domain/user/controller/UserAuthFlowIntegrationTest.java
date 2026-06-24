package com.fxflow.domain.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fxflow.domain.user.dto.request.LoginRequest;
import com.fxflow.domain.user.dto.request.SignupRequest;
import com.fxflow.domain.user.dto.request.WithdrawRequest;
import com.fxflow.domain.user.entity.User;
import com.fxflow.domain.user.repository.UserRepository;
import com.fxflow.domain.user.enums.UserStatus;
import com.fxflow.support.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
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
@DisplayName("회원가입/로그인/로그아웃/회원탈퇴 통합 테스트")
class UserAuthFlowIntegrationTest extends AbstractIntegrationTest {

    private static final String EMAIL = "flow-user@example.com";
    private static final String NAME = "홍길동";
    private static final String PASSWORD = "Abcd1234!";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
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
    @DisplayName("회원가입 성공: 201 응답과 함께 DB에 회원이 저장된다")
    void signup_success() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new SignupRequest(NAME, EMAIL, PASSWORD))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.name").value(NAME));

        assertThat(userRepository.findByEmail(EMAIL)).isPresent();
    }

    @Test
    @DisplayName("회원가입 실패: 이메일이 중복되면 409 EMAIL_DUPLICATED를 반환한다")
    void signup_fail_emailDuplicated() throws Exception {
        signup();

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new SignupRequest(NAME, EMAIL, PASSWORD))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_DUPLICATED"));
    }

    @Test
    @DisplayName("로그인 성공: 200 응답과 accessToken/refreshToken 쿠키가 발급된다")
    void login_success() throws Exception {
        signup();

        MockHttpServletResponse response = login(EMAIL, PASSWORD);

        assertThat(response.getCookie("accessToken")).isNotNull();
        assertThat(response.getCookie("refreshToken")).isNotNull();
    }

    @Test
    @DisplayName("로그인 실패: 비밀번호가 일치하지 않으면 401 INVALID_CREDENTIALS를 반환한다")
    void login_fail_wrongPassword() throws Exception {
        signup();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LoginRequest(EMAIL, "WrongPass1!"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("로그아웃 성공: 쿠키가 삭제되고, 로그아웃 시점의 refreshToken으로는 재발급을 받을 수 없다")
    void logout_success_invalidatesRefreshToken() throws Exception {
        signup();
        MockHttpServletResponse loginResponse = login(EMAIL, PASSWORD);
        Cookie accessTokenCookie = loginResponse.getCookie("accessToken");
        Cookie refreshTokenCookie = loginResponse.getCookie("refreshToken");

        MvcResult logoutResult = mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(accessTokenCookie, refreshTokenCookie))
                .andExpect(status().isOk())
                .andReturn();

        Cookie deletedAccessCookie = logoutResult.getResponse().getCookie("accessToken");
        assertThat(deletedAccessCookie.getMaxAge()).isZero();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshTokenCookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("회원탈퇴 성공: 탈퇴 후에는 같은 자격증명으로 로그인할 수 없다")
    void withdraw_success_blocksFutureLogin() throws Exception {
        signup();
        MockHttpServletResponse loginResponse = login(EMAIL, PASSWORD);
        Cookie accessTokenCookie = loginResponse.getCookie("accessToken");

        mockMvc.perform(delete("/api/v1/auth/me")
                        .cookie(accessTokenCookie)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new WithdrawRequest(PASSWORD))))
                .andExpect(status().isOk());

        User withdrawn = userRepository.findAll().stream()
                .filter(user -> user.getId() != null)
                .findFirst()
                .orElseThrow();
        assertThat(withdrawn.getStatus()).isEqualTo(UserStatus.WITHDRAWN);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LoginRequest(EMAIL, PASSWORD))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("회원탈퇴 실패: 비밀번호가 일치하지 않으면 401을 반환하고 탈퇴되지 않는다")
    void withdraw_fail_wrongPassword() throws Exception {
        signup();
        MockHttpServletResponse loginResponse = login(EMAIL, PASSWORD);
        Cookie accessTokenCookie = loginResponse.getCookie("accessToken");

        mockMvc.perform(delete("/api/v1/auth/me")
                        .cookie(accessTokenCookie)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new WithdrawRequest("WrongPass1!"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        User user = userRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    private void signup() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(new SignupRequest(NAME, EMAIL, PASSWORD))));
    }

    private MockHttpServletResponse login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse();
    }

    private void truncateAll() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    wallets,
                    users
                RESTART IDENTITY CASCADE
                """);
    }
}
