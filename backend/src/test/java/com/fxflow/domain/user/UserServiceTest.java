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
    // ── 비밀번호 변경 ───────────────────────────────────────────────────────
    @Nested
    @DisplayName("비밀번호 변경")
    class ChangePassword {

        private static final Long USER_ID = 1L;
        private static final String CURRENT_RAW = "OldPass123!";
        private static final String CURRENT_HASH = "encodedOldPassword";
        private static final String NEW_RAW = "NewPass456!";
        private static final String NEW_HASH = "encodedNewPassword";

        @Test
        @DisplayName("성공: 현재 비밀번호가 일치하고 새 비밀번호가 다르면 비밀번호 해시가 갱신된다")
        void success() {
            // given
            User user = User.create("hong@example.com", CURRENT_HASH, "홍길동");

            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(CURRENT_RAW, CURRENT_HASH)).thenReturn(true);
            when(passwordEncoder.matches(NEW_RAW, CURRENT_HASH)).thenReturn(false);
            when(passwordEncoder.encode(NEW_RAW)).thenReturn(NEW_HASH);

            // when
            userService.changePassword(USER_ID, CURRENT_RAW, NEW_RAW);

            // then
            assertThat(user.getPasswordHash()).isEqualTo(NEW_HASH);
        }

        @Test
        @DisplayName("실패: 존재하지 않는 유저면 USER_NOT_FOUND 예외가 발생한다")
        void fail_userNotFound() {
            // given
            when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> userService.changePassword(USER_ID, CURRENT_RAW, NEW_RAW))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(UserErrorCode.USER_NOT_FOUND);

            verify(passwordEncoder, never()).encode(anyString());
        }

        @Test
        @DisplayName("실패: 현재 비밀번호가 일치하지 않으면 PASSWORD_MISMATCH 예외가 발생한다")
        void fail_currentPasswordMismatch() {
            // given
            User user = User.create("hong@example.com", CURRENT_HASH, "홍길동");

            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(CURRENT_RAW, CURRENT_HASH)).thenReturn(false);

            // when & then
            assertThatThrownBy(() -> userService.changePassword(USER_ID, CURRENT_RAW, NEW_RAW))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(UserErrorCode.PASSWORD_MISMATCH);

            verify(passwordEncoder, never()).encode(anyString());
            assertThat(user.getPasswordHash()).isEqualTo(CURRENT_HASH);
        }

        @Test
        @DisplayName("실패: 새 비밀번호가 기존 비밀번호와 동일하면 SAME_AS_OLD_PASSWORD 예외가 발생한다")
        void fail_sameAsOldPassword() {
            // given
            User user = User.create("hong@example.com", CURRENT_HASH, "홍길동");

            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(CURRENT_RAW, CURRENT_HASH)).thenReturn(true);
            when(passwordEncoder.matches(NEW_RAW, CURRENT_HASH)).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> userService.changePassword(USER_ID, CURRENT_RAW, NEW_RAW))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(UserErrorCode.SAME_AS_OLD_PASSWORD);

            verify(passwordEncoder, never()).encode(anyString());
            assertThat(user.getPasswordHash()).isEqualTo(CURRENT_HASH);
        }
    }

}