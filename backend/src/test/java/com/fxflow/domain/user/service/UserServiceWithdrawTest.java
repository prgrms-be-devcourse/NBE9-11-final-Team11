package com.fxflow.domain.user.service;

import com.fxflow.domain.remittancetransaction.repository.RemittanceTransactionRepository;
import com.fxflow.domain.user.dto.response.WithdrawUserResponse;
import com.fxflow.domain.user.entity.User;
import com.fxflow.domain.user.enums.UserStatus;
import com.fxflow.domain.user.errorcode.UserErrorCode;
import com.fxflow.domain.user.repository.UserRepository;
import com.fxflow.domain.wallet.entity.Wallet;
import com.fxflow.domain.wallet.repository.ExchangeTransactionRepository;
import com.fxflow.domain.wallet.repository.P2pTransferRepository;
import com.fxflow.domain.wallet.repository.WalletRepository;
import com.fxflow.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("회원 탈퇴 테스트")
class UserServiceWithdrawTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private WalletRepository walletRepository;
    @Mock
    private RemittanceTransactionRepository remittanceTransactionRepository;
    @Mock
    private ExchangeTransactionRepository exchangeTransactionRepository;
    @Mock
    private P2pTransferRepository p2pTransferRepository;

    @InjectMocks
    private UserService userService;

    private static final Long USER_ID = 1L;
    private static final String RAW_PASSWORD = "Password123!";
    private static final String ENCODED_PASSWORD = "encoded-password";

    private User activeUser;

    @BeforeEach
    void setUp() {
        activeUser = User.create("test@fxflow.app", ENCODED_PASSWORD, "홍길동");
        ReflectionTestUtils.setField(activeUser, "id", USER_ID);
    }

    @Nested
    @DisplayName("정상 케이스")
    class SuccessCase {

        @Test
        @DisplayName("잔액 없고, 진행 중 거래 없고, 비밀번호가 맞으면 탈퇴에 성공한다")
        void withdraw_success() {
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(activeUser));
            given(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).willReturn(true);
            given(walletRepository.findByUserId(USER_ID)).willReturn(List.of());
            given(remittanceTransactionRepository.existsByUserIdAndStatusIn(anyLong(), any()))
                    .willReturn(false);
            WithdrawUserResponse response = userService.withDrawn(USER_ID, RAW_PASSWORD);

            assertThat(response.userId()).isEqualTo(USER_ID);
            assertThat(response.status()).isEqualTo(UserStatus.WITHDRAWN.name());
            assertThat(activeUser.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
            assertThat(activeUser.getEmail()).isEqualTo("del_" + USER_ID + "@delect.com");
            assertThat(activeUser.getName()).isEqualTo("탈퇴한 회원_" + USER_ID);
            assertThat(activeUser.getPasswordHash()).isEmpty();
        }
    }

    @Nested
    @DisplayName("실패 케이스")
    class FailureCase {

        @Test
        @DisplayName("존재하지 않는 유저면 USER_NOT_FOUND 예외가 발생한다")
        void withdraw_fail_userNotFound() {
            given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> userService.withDrawn(USER_ID, RAW_PASSWORD))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", UserErrorCode.USER_NOT_FOUND);
        }

        @Test
        @DisplayName("이미 탈퇴한 회원이면 ALREADY_WITHDRAWN 예외가 발생한다")
        void withdraw_fail_alreadyWithdrawn() {
            activeUser.withdraw("del_1@delect.com", "탈퇴한 회원_1");
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(activeUser));

            assertThatThrownBy(() -> userService.withDrawn(USER_ID, RAW_PASSWORD))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", UserErrorCode.ALREADY_WITHDRAWN);

            verify(passwordEncoder, never()).matches(any(), any());
        }

        @Test
        @DisplayName("비밀번호가 일치하지 않으면 INVALID_CREDENTIALS 예외가 발생한다")
        void withdraw_fail_wrongPassword() {
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(activeUser));
            given(passwordEncoder.matches("wrong-password", ENCODED_PASSWORD)).willReturn(false);

            assertThatThrownBy(() -> userService.withDrawn(USER_ID, "wrong-password"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", UserErrorCode.INVALID_CREDENTIALS);

            verify(walletRepository, never()).findByUserId(any());
        }

        @Test
        @DisplayName("월렛 잔액이 남아있으면 WITHDRAWAL_BLOCKED 예외가 발생한다")
        void withdraw_fail_hasBalance() {
            Wallet krwWallet = Wallet.create(activeUser, "KRW", new BigDecimal("10000"));
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(activeUser));
            given(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).willReturn(true);
            given(walletRepository.findByUserId(USER_ID)).willReturn(List.of(krwWallet));

            assertThatThrownBy(() -> userService.withDrawn(USER_ID, RAW_PASSWORD))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", UserErrorCode.WITHDRAWAL_BLOCKED);
        }
    }
}