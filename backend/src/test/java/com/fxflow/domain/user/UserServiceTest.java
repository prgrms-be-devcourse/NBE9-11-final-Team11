package com.fxflow.domain.user;

import com.fxflow.domain.user.dto.request.LoginRequest;
import com.fxflow.domain.user.dto.request.SignupRequest;
import com.fxflow.domain.user.dto.response.SignupResponse;
import com.fxflow.domain.user.entity.User;
import com.fxflow.domain.user.errorcode.UserErrorCode;
import com.fxflow.domain.user.repository.UserRepository;
import com.fxflow.domain.user.service.UserService;
import com.fxflow.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    // ── 회원가입 ───────────────────────────────────────────────────────────
    @Nested
    @DisplayName("회원가입")
    class Signup {

        @Test
        @DisplayName("성공: 정상 회원가입")
        void success() {
            // given
            SignupRequest request = new SignupRequest("홍길동", "hong@example.com", "Abcd1234!");

            when(userRepository.existsByEmail(request.email())).thenReturn(false);
            when(passwordEncoder.encode(request.password())).thenReturn("encodedPassword");
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // when
            SignupResponse response = userService.signup(request);

            // then
            assertThat(response.email()).isEqualTo("hong@example.com");
            assertThat(response.name()).isEqualTo("홍길동");
            assertThat(response.mockAccountLinked()).isFalse();
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("실패: 이메일 중복")
        void fail_emailDuplicated() {
            // given
            SignupRequest request = new SignupRequest("홍길동", "hong@example.com", "Abcd1234!");

            when(userRepository.existsByEmail(request.email())).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> userService.signup(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(UserErrorCode.EMAIL_DUPLICATED);

            verify(userRepository, never()).save(any(User.class));
        }
    }

    // ── 로그인 ─────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("로그인")
    class Login {

        @Test
        @DisplayName("성공: 정상 로그인")
        void success() {
            // given
            LoginRequest request = new LoginRequest("hong@example.com", "Abcd1234!");

            User user = User.create("hong@example.com", "encodedPassword", "홍길동");

            when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(request.password(), user.getPasswordHash())).thenReturn(true);

            // when & then
            assertThatCode(() -> userService.login(request))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("실패: 이메일 없음")
        void fail_emailNotFound() {
            // given
            LoginRequest request = new LoginRequest("notexist@example.com", "Abcd1234!");

            when(userRepository.findByEmail(request.email())).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> userService.login(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(UserErrorCode.INVALID_CREDENTIALS);
        }

        @Test
        @DisplayName("실패: 비밀번호 불일치")
        void fail_passwordMismatch() {
            // given
            LoginRequest request = new LoginRequest("hong@example.com", "wrongPassword");

            User user = User.create("hong@example.com", "encodedPassword", "홍길동");

            when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(request.password(), user.getPasswordHash())).thenReturn(false);

            // when & then
            assertThatThrownBy(() -> userService.login(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(UserErrorCode.INVALID_CREDENTIALS);
        }

        @Test
        @DisplayName("실패: 탈퇴한 회원")
        void fail_withdrawnUser() {
            // given
            LoginRequest request = new LoginRequest("hong@example.com", "Abcd1234!");

            User user = User.create("hong@example.com", "encodedPassword", "홍길동");
            user.withdraw("del_1@delect.com", "탈퇴한 회원_1");
            when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(user));

            // when & then
            assertThatThrownBy(() -> userService.login(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(UserErrorCode.INVALID_CREDENTIALS);

            verify(passwordEncoder, never()).matches(anyString(), anyString());
        }
    }
}