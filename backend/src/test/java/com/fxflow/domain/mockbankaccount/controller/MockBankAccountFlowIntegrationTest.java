package com.fxflow.domain.mockbankaccount.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fxflow.domain.mockbankaccount.dto.request.MockBankAccountCheckRequest;
import com.fxflow.domain.mockbankaccount.dto.request.MockBankLinkRequest;
import com.fxflow.domain.mockbankaccount.dto.request.UsdMockAccountInquiryRequest;
import com.fxflow.domain.mockbankaccount.entity.MockBankAccount;
import com.fxflow.domain.mockbankaccount.repository.MockBankAccountRepository;
import com.fxflow.domain.user.dto.request.LoginRequest;
import com.fxflow.domain.user.dto.request.SignupRequest;
import com.fxflow.domain.user.entity.User;
import com.fxflow.domain.user.repository.UserRepository;
import com.fxflow.support.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@DisplayName("모의계좌(MockBankAccount) 연동/조회 통합 테스트")
class MockBankAccountFlowIntegrationTest extends AbstractIntegrationTest {

    private static final String EMAIL = "mockbank-user@example.com";
    private static final String NAME = "모의계좌사용자";
    private static final String PASSWORD = "Abcd1234!";
    private static final String VALID_ACCOUNT_NUMBER = "123456789012";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private MockBankAccountRepository mockBankAccountRepository;
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
    @DisplayName("계좌번호 사전 확인 성공: 형식이 올바르고 중복되지 않으면 available=true를 반환한다")
    void checkAccountNumber_available() throws Exception {
        mockMvc.perform(post("/api/v1/mockbank/check")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new MockBankAccountCheckRequest(VALID_ACCOUNT_NUMBER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    @DisplayName("계좌번호 사전 확인 실패: 형식이 올바르지 않으면 available=false를 반환한다")
    void checkAccountNumber_invalidFormat() throws Exception {
        mockMvc.perform(post("/api/v1/mockbank/check")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new MockBankAccountCheckRequest("abc123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));
    }

    @Test
    @DisplayName("계좌번호 사전 확인 실패: 이미 사용 중인 계좌번호면 available=false를 반환한다")
    void checkAccountNumber_duplicated() throws Exception {
        signupAndLink(EMAIL, NAME, VALID_ACCOUNT_NUMBER);

        mockMvc.perform(post("/api/v1/mockbank/check")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new MockBankAccountCheckRequest(VALID_ACCOUNT_NUMBER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));
    }

    @Test
    @DisplayName("모의계좌 연결 성공: 201 응답과 함께 1,000만원이 입금된 KRW 계좌가 생성된다")
    void linkAccount_success() throws Exception {
        Cookie accessTokenCookie = signupAndLogin(EMAIL, NAME, PASSWORD);

        mockMvc.perform(post("/api/v1/mockbank/link")
                        .cookie(accessTokenCookie)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new MockBankLinkRequest("국민은행", VALID_ACCOUNT_NUMBER))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mockAccount.accountNumber").value(VALID_ACCOUNT_NUMBER));

        User user = userRepository.findByEmail(EMAIL).orElseThrow();
        MockBankAccount account = mockBankAccountRepository
                .findFirstByUser_IdAndCurrencyCodeAndDeletedAtIsNull(user.getId(), "KRW")
                .orElseThrow();
        assertThat(account.getBalance()).isEqualByComparingTo(new BigDecimal("10000000"));
    }

    @Test
    @DisplayName("모의계좌 연결 실패: 인증 토큰이 없으면 401을 반환한다")
    void linkAccount_fail_unauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/mockbank/link")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new MockBankLinkRequest("국민은행", VALID_ACCOUNT_NUMBER))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("모의계좌 연결 실패: 이미 연결된 사용자가 다시 연결을 시도하면 409 MOCK_ACCOUNT_ALREADY_LINKED를 반환한다")
    void linkAccount_fail_alreadyLinked() throws Exception {
        Cookie accessTokenCookie = signupAndLogin(EMAIL, NAME, PASSWORD);
        mockMvc.perform(post("/api/v1/mockbank/link")
                .cookie(accessTokenCookie)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(new MockBankLinkRequest("국민은행", VALID_ACCOUNT_NUMBER))));

        mockMvc.perform(post("/api/v1/mockbank/link")
                        .cookie(accessTokenCookie)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new MockBankLinkRequest("국민은행", "999999999999"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MOCK_ACCOUNT_ALREADY_LINKED"));
    }

    @Test
    @DisplayName("모의계좌 연결 실패: 계좌번호 형식이 올바르지 않으면 400을 반환한다")
    void linkAccount_fail_invalidFormat() throws Exception {
        Cookie accessTokenCookie = signupAndLogin(EMAIL, NAME, PASSWORD);

        mockMvc.perform(post("/api/v1/mockbank/link")
                        .cookie(accessTokenCookie)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new MockBankLinkRequest("국민은행", "12-34"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MOCK_ACCOUNT_INVALID_FORMAT"));
    }

    @Test
    @DisplayName("내 모의계좌 조회 성공: 연결된 KRW 계좌 정보를 반환한다")
    void getMyMockAccount_success() throws Exception {
        Cookie accessTokenCookie = signupAndLink(EMAIL, NAME, VALID_ACCOUNT_NUMBER);

        mockMvc.perform(get("/api/v1/users/me/mock-account")
                        .cookie(accessTokenCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber").value(VALID_ACCOUNT_NUMBER))
                .andExpect(jsonPath("$.balance").value(10000000));
    }

    @Test
    @DisplayName("내 모의계좌 조회 실패: 연결된 계좌가 없으면 404를 반환한다")
    void getMyMockAccount_fail_notLinked() throws Exception {
        Cookie accessTokenCookie = signupAndLogin(EMAIL, NAME, PASSWORD);

        mockMvc.perform(get("/api/v1/users/me/mock-account")
                        .cookie(accessTokenCookie))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MOCK_ACCOUNT_NOT_FOUND"));
    }

    @Test
    @DisplayName("USD 모의계좌 조회 성공: 이름/은행명/계좌번호가 일치하면 잔액을 반환한다")
    void inquireUsdAccount_success() throws Exception {
        User recipient = userRepository.save(User.create("usd-recipient@example.com", "encoded", "수취인"));
        mockBankAccountRepository.save(MockBankAccount.createSeedAccount(
                recipient, "USD", "Chase Bank", "987654321001", new BigDecimal("500.00")
        ));

        mockMvc.perform(post("/api/v1/mockbank/inquiry/usd")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new UsdMockAccountInquiryRequest("Chase Bank", "987654321001", "수취인"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(500.00))
                .andExpect(jsonPath("$.currencyCode").value("USD"));
    }

    @Test
    @DisplayName("USD 모의계좌 조회 실패: 정보가 일치하지 않으면 404를 반환한다")
    void inquireUsdAccount_fail_notFound() throws Exception {
        mockMvc.perform(post("/api/v1/mockbank/inquiry/usd")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new UsdMockAccountInquiryRequest("Chase Bank", "000000000000", "없는사람"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MOCK_ACCOUNT_NOT_FOUND"));
    }

    private Cookie signupAndLogin(String email, String name, String password) throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(new SignupRequest(name, email, password))));

        MockHttpServletResponse loginResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse();

        return loginResponse.getCookie("accessToken");
    }

    private Cookie signupAndLink(String email, String name, String accountNumber) throws Exception {
        Cookie accessTokenCookie = signupAndLogin(email, name, PASSWORD);

        mockMvc.perform(post("/api/v1/mockbank/link")
                .cookie(accessTokenCookie)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(new MockBankLinkRequest("국민은행", accountNumber))));

        return accessTokenCookie;
    }

    private void truncateAll() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    ledger_entries,
                    mock_bank_accounts,
                    wallets,
                    users
                RESTART IDENTITY CASCADE
                """);
    }
}
