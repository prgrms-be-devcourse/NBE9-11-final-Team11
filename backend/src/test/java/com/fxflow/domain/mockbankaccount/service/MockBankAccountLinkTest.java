package com.fxflow.domain.mockbankaccount.service;

import com.fxflow.domain.mockbankaccount.dto.response.MockBankLinkResponse;
import com.fxflow.domain.mockbankaccount.entity.MockBankAccount;
import com.fxflow.domain.mockbankaccount.errorcode.MockBankAccountErrorCode;
import com.fxflow.domain.mockbankaccount.repository.MockBankAccountRepository;
import com.fxflow.domain.user.entity.User;
import com.fxflow.domain.user.errorcode.UserErrorCode;
import com.fxflow.domain.user.repository.UserRepository;
import com.fxflow.domain.wallet.entity.Wallet;
import com.fxflow.domain.wallet.repository.WalletRepository;
import com.fxflow.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MockBankAccountLinkTest {

    @Mock
    private MockBankAccountRepository mockBankAccountRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private WalletRepository walletRepository;

    @InjectMocks
    private MockBankAccountService mockBankAccountService;

    @Mock
    private User user;

    private static final Long USER_ID = 1L;
    private static final String BANK_NAME = "신한은행";
    private static final String VALID_ACCOUNT_NUMBER = "123456789012"; // 12자리

    // ── 모의계좌 연결 ──────────────────────────────
    @Nested
    @DisplayName("모의계좌 연결")
    class LinkAccount {

        @Test
        @DisplayName("성공: 정상 연결 시 KRW 계좌 + KRW/USD Wallet 생성")
        void success() {
            // given
            when(user.getId()).thenReturn(USER_ID);
            when(mockBankAccountRepository.existsByUserIdAndCurrencyCode(USER_ID, "KRW"))
                    .thenReturn(false);
            when(mockBankAccountRepository.existsByAccountNumber(VALID_ACCOUNT_NUMBER))
                    .thenReturn(false);
            when(userRepository.findById(USER_ID))
                    .thenReturn(Optional.of(user));
            when(mockBankAccountRepository.save(any(MockBankAccount.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(walletRepository.findByUserIdAndCurrencyCode(USER_ID, "KRW"))
                    .thenReturn(Optional.empty());
            when(walletRepository.findByUserIdAndCurrencyCode(USER_ID, "USD"))
                    .thenReturn(Optional.empty());
            when(walletRepository.save(any(Wallet.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            MockBankLinkResponse response = mockBankAccountService.linkAccount(USER_ID, BANK_NAME, VALID_ACCOUNT_NUMBER);

            // then
            assertThat(response.mockAccount().bankName()).isEqualTo(BANK_NAME);
            assertThat(response.mockAccount().accountNumber()).isEqualTo(VALID_ACCOUNT_NUMBER);
            assertThat(response.mockAccount().currency()).isEqualTo("KRW");
            assertThat(response.wallets()).hasSize(2);

            verify(mockBankAccountRepository).save(any(MockBankAccount.class));
            verify(walletRepository, times(2)).save(any(Wallet.class));
        }

        @Test
        @DisplayName("실패: 이미 연결된 계좌가 있음")
        void fail_alreadyLinked() {
            // given
            when(mockBankAccountRepository.existsByUserIdAndCurrencyCode(USER_ID, "KRW"))
                    .thenReturn(true);

            // when & then
            assertThatThrownBy(() -> mockBankAccountService.linkAccount(USER_ID, BANK_NAME, VALID_ACCOUNT_NUMBER))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(MockBankAccountErrorCode.MOCK_ACCOUNT_ALREADY_LINKED);

            verify(mockBankAccountRepository, never()).save(any(MockBankAccount.class));
        }

        @Test
        @DisplayName("실패: 계좌번호 중복")
        void fail_accountNumberDuplicated() {
            // given
            when(mockBankAccountRepository.existsByUserIdAndCurrencyCode(USER_ID, "KRW"))
                    .thenReturn(false);
            when(mockBankAccountRepository.existsByAccountNumber(VALID_ACCOUNT_NUMBER))
                    .thenReturn(true);

            // when & then
            assertThatThrownBy(() -> mockBankAccountService.linkAccount(USER_ID, BANK_NAME, VALID_ACCOUNT_NUMBER))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(MockBankAccountErrorCode.MOCK_ACCOUNT_NUMBER_DUPLICATED);

            verify(mockBankAccountRepository, never()).save(any(MockBankAccount.class));
        }

        @Test
        @DisplayName("실패: 유저를 찾을 수 없음")
        void fail_userNotFound() {
            // given
            when(mockBankAccountRepository.existsByUserIdAndCurrencyCode(USER_ID, "KRW"))
                    .thenReturn(false);
            when(mockBankAccountRepository.existsByAccountNumber(VALID_ACCOUNT_NUMBER))
                    .thenReturn(false);
            when(userRepository.findById(USER_ID))
                    .thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> mockBankAccountService.linkAccount(USER_ID, BANK_NAME, VALID_ACCOUNT_NUMBER))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(UserErrorCode.USER_NOT_FOUND);
        }

        @Test
        @DisplayName("실패: 계좌번호가 null이거나 빈 문자열")
        void fail_accountNumberBlank() {
            // when & then
            assertThatThrownBy(() -> mockBankAccountService.linkAccount(USER_ID, BANK_NAME, ""))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(MockBankAccountErrorCode.MOCK_ACCOUNT_INVALID_FORMAT);
        }

        @Test
        @DisplayName("실패: 계좌번호에 숫자외 문자가 포함됨")
        void fail_invalidCharacter() {
            // when & then
            assertThatThrownBy(() -> mockBankAccountService.linkAccount(USER_ID, BANK_NAME, "12345678901a"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(MockBankAccountErrorCode.MOCK_ACCOUNT_INVALID_FORMAT);
        }

        @Test
        @DisplayName("실패: 자릿수가 12자리가 아님")
        void fail_invalidDigitLength() {
            // when & then
            assertThatThrownBy(() -> mockBankAccountService.linkAccount(USER_ID, BANK_NAME, "123456789"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(MockBankAccountErrorCode.MOCK_ACCOUNT_INVALID_FORMAT);
        }
    }
}
