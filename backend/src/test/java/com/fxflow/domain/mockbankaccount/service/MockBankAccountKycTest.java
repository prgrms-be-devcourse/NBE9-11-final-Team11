package com.fxflow.domain.mockbankaccount.service;

import com.fxflow.domain.mockbankaccount.dto.response.KycInquiryResponse;
import com.fxflow.domain.mockbankaccount.dto.response.KycStartResponse;
import com.fxflow.domain.mockbankaccount.dto.response.MockBankLinkResponse;
import com.fxflow.domain.mockbankaccount.entity.KycVerification;
import com.fxflow.domain.mockbankaccount.entity.MockBankAccount;
import com.fxflow.domain.mockbankaccount.enums.KycVerificationStatus;
import com.fxflow.domain.mockbankaccount.errorcode.MockBankAccountErrorCode;
import com.fxflow.domain.mockbankaccount.repository.KycVerificationRepository;
import com.fxflow.domain.mockbankaccount.repository.MockBankAccountRepository;
import com.fxflow.domain.user.entity.User;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MockBankAccountKycTest {

    @Mock
    private MockBankAccountRepository mockBankAccountRepository;

    @Mock
    private KycVerificationRepository kycVerificationRepository;

    @Mock
    private KycAttemptService kycAttemptService;

    @Mock
    private com.fxflow.domain.user.repository.UserRepository userRepository;

    @Mock
    private WalletRepository walletRepository;

    @InjectMocks
    private MockBankAccountService mockBankAccountService;

    @Mock
    private User user;

    private static final Long USER_ID = 1L;
    private static final Long VERIFICATION_ID = 10L;
    private static final String BANK_NAME = "국민은행";
    private static final String ACCOUNT_NUMBER = "123456789012";
    private static final String HOLDER_NAME = "홍길동";
    private static final String CODE = "1234";

    private KycVerification newVerification() {
        return KycVerification.create(
                user, BANK_NAME, ACCOUNT_NUMBER, HOLDER_NAME, CODE,
                LocalDateTime.now().plusMinutes(5)
        );
    }

    // ── 1원 인증 시작 ──────────────────────────────
    @Nested
    @DisplayName("1원 인증 시작 (startKyc)")
    class StartKyc {

        @Test
        @DisplayName("성공: 코드가 발급되고 오늘 남은 요청 횟수가 함께 반환된다")
        void success() {
            // given
            when(mockBankAccountRepository.existsByUserIdAndCurrencyCode(USER_ID, "KRW"))
                    .thenReturn(false);
            when(mockBankAccountRepository.existsByAccountNumber(ACCOUNT_NUMBER))
                    .thenReturn(false);
            when(kycVerificationRepository.countByUserIdAndCreatedAtAfter(eq(USER_ID), any()))
                    .thenReturn(0L);
            when(userRepository.findById(USER_ID))
                    .thenReturn(Optional.of(user));
            when(kycVerificationRepository.findAllByUserIdAndStatus(USER_ID, KycVerificationStatus.PENDING))
                    .thenReturn(List.of());
            when(kycVerificationRepository.save(any(KycVerification.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            KycStartResponse response = mockBankAccountService.startKyc(USER_ID, BANK_NAME, ACCOUNT_NUMBER, HOLDER_NAME);

            // then
            assertThat(response.remainingDailyRequests()).isEqualTo(4); // 5회 한도 - 이번 요청 1회
            verify(kycVerificationRepository).save(any(KycVerification.class));
        }

        @Test
        @DisplayName("성공: 이전에 발급된 대기 중 인증 건은 만료 처리된다")
        void success_expiresPreviousPending() {
            // given
            KycVerification previous = newVerification();
            when(mockBankAccountRepository.existsByUserIdAndCurrencyCode(USER_ID, "KRW"))
                    .thenReturn(false);
            when(mockBankAccountRepository.existsByAccountNumber(ACCOUNT_NUMBER))
                    .thenReturn(false);
            when(kycVerificationRepository.countByUserIdAndCreatedAtAfter(eq(USER_ID), any()))
                    .thenReturn(1L);
            when(userRepository.findById(USER_ID))
                    .thenReturn(Optional.of(user));
            when(kycVerificationRepository.findAllByUserIdAndStatus(USER_ID, KycVerificationStatus.PENDING))
                    .thenReturn(List.of(previous));
            when(kycVerificationRepository.save(any(KycVerification.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            mockBankAccountService.startKyc(USER_ID, BANK_NAME, ACCOUNT_NUMBER, HOLDER_NAME);

            // then
            assertThat(previous.getStatus()).isEqualTo(KycVerificationStatus.EXPIRED);
        }

        @Test
        @DisplayName("실패: 이미 연결된 모의계좌가 있음")
        void fail_alreadyLinked() {
            when(mockBankAccountRepository.existsByUserIdAndCurrencyCode(USER_ID, "KRW"))
                    .thenReturn(true);

            assertThatThrownBy(() -> mockBankAccountService.startKyc(USER_ID, BANK_NAME, ACCOUNT_NUMBER, HOLDER_NAME))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(MockBankAccountErrorCode.MOCK_ACCOUNT_ALREADY_LINKED);

            verify(kycVerificationRepository, never()).save(any());
        }

        @Test
        @DisplayName("실패: 계좌번호가 이미 사용 중")
        void fail_accountNumberDuplicated() {
            when(mockBankAccountRepository.existsByUserIdAndCurrencyCode(USER_ID, "KRW"))
                    .thenReturn(false);
            when(mockBankAccountRepository.existsByAccountNumber(ACCOUNT_NUMBER))
                    .thenReturn(true);

            assertThatThrownBy(() -> mockBankAccountService.startKyc(USER_ID, BANK_NAME, ACCOUNT_NUMBER, HOLDER_NAME))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(MockBankAccountErrorCode.MOCK_ACCOUNT_NUMBER_DUPLICATED);
        }

        @Test
        @DisplayName("실패: 오늘 발급 한도(5회)를 초과함")
        void fail_dailyLimitExceeded() {
            when(mockBankAccountRepository.existsByUserIdAndCurrencyCode(USER_ID, "KRW"))
                    .thenReturn(false);
            when(mockBankAccountRepository.existsByAccountNumber(ACCOUNT_NUMBER))
                    .thenReturn(false);
            when(kycVerificationRepository.countByUserIdAndCreatedAtAfter(eq(USER_ID), any()))
                    .thenReturn(5L);

            assertThatThrownBy(() -> mockBankAccountService.startKyc(USER_ID, BANK_NAME, ACCOUNT_NUMBER, HOLDER_NAME))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(MockBankAccountErrorCode.KYC_DAILY_LIMIT_EXCEEDED);

            verify(kycVerificationRepository, never()).save(any());
        }

        @Test
        @DisplayName("실패: 계좌번호 형식이 올바르지 않음")
        void fail_invalidAccountNumberFormat() {
            assertThatThrownBy(() -> mockBankAccountService.startKyc(USER_ID, BANK_NAME, "abc", HOLDER_NAME))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(MockBankAccountErrorCode.MOCK_ACCOUNT_INVALID_FORMAT);
        }
    }

    // ── 계좌번호 조회 ──────────────────────────────
    @Nested
    @DisplayName("계좌번호 조회 (inquireKyc)")
    class InquireKyc {

        @Test
        @DisplayName("성공: 입금자명(fxflow+코드)을 반환한다")
        void success() {
            KycVerification verification = newVerification();
            when(kycVerificationRepository
                    .findFirstByBankNameAndAccountNumberAndAccountHolderNameAndStatusOrderByCreatedAtDesc(
                            BANK_NAME, ACCOUNT_NUMBER, HOLDER_NAME, KycVerificationStatus.PENDING))
                    .thenReturn(Optional.of(verification));

            KycInquiryResponse response = mockBankAccountService.inquireKyc(BANK_NAME, ACCOUNT_NUMBER, HOLDER_NAME);

            assertThat(response.depositorName()).isEqualTo("fxflow" + CODE);
        }

        @Test
        @DisplayName("실패: 일치하는 대기 중 인증 건이 없음")
        void fail_notFound() {
            when(kycVerificationRepository
                    .findFirstByBankNameAndAccountNumberAndAccountHolderNameAndStatusOrderByCreatedAtDesc(
                            BANK_NAME, ACCOUNT_NUMBER, HOLDER_NAME, KycVerificationStatus.PENDING))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> mockBankAccountService.inquireKyc(BANK_NAME, ACCOUNT_NUMBER, HOLDER_NAME))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(MockBankAccountErrorCode.KYC_VERIFICATION_NOT_FOUND);
        }

        @Test
        @DisplayName("실패: 인증 시간이 만료됨")
        void fail_expired() {
            KycVerification verification = KycVerification.create(
                    user, BANK_NAME, ACCOUNT_NUMBER, HOLDER_NAME, CODE,
                    LocalDateTime.now().minusSeconds(1)
            );
            when(kycVerificationRepository
                    .findFirstByBankNameAndAccountNumberAndAccountHolderNameAndStatusOrderByCreatedAtDesc(
                            BANK_NAME, ACCOUNT_NUMBER, HOLDER_NAME, KycVerificationStatus.PENDING))
                    .thenReturn(Optional.of(verification));

            assertThatThrownBy(() -> mockBankAccountService.inquireKyc(BANK_NAME, ACCOUNT_NUMBER, HOLDER_NAME))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(MockBankAccountErrorCode.KYC_CODE_EXPIRED);
        }
    }

    // ── 인증코드 검증 ──────────────────────────────
    @Nested
    @DisplayName("인증코드 검증 (verifyKyc)")
    class VerifyKyc {

        @Test
        @DisplayName("성공: 코드가 일치하면 모의계좌 연결까지 완료한다")
        void success() {
            // given
            KycVerification verification = newVerification();
            when(kycVerificationRepository.findByIdAndUserId(VERIFICATION_ID, USER_ID))
                    .thenReturn(Optional.of(verification));
            when(user.getId()).thenReturn(USER_ID);
            when(mockBankAccountRepository.existsByUserIdAndCurrencyCode(USER_ID, "KRW"))
                    .thenReturn(false);
            when(mockBankAccountRepository.existsByAccountNumber(ACCOUNT_NUMBER))
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
            MockBankLinkResponse response = mockBankAccountService.verifyKyc(USER_ID, VERIFICATION_ID, CODE);

            // then
            assertThat(verification.getStatus()).isEqualTo(KycVerificationStatus.VERIFIED);
            assertThat(response.mockAccount().bankName()).isEqualTo(BANK_NAME);
            verify(kycAttemptService, never()).recordFailedAttempt(anyLong());
        }

        @Test
        @DisplayName("실패: 코드가 일치하지 않으면 실패 시도횟수를 기록하고 예외를 던진다")
        void fail_codeMismatch() {
            // given
            KycVerification verification = newVerification();
            when(kycVerificationRepository.findByIdAndUserId(VERIFICATION_ID, USER_ID))
                    .thenReturn(Optional.of(verification));
            when(kycAttemptService.recordFailedAttempt(VERIFICATION_ID))
                    .thenReturn(4);

            // when & then
            assertThatThrownBy(() -> mockBankAccountService.verifyKyc(USER_ID, VERIFICATION_ID, "0000"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(MockBankAccountErrorCode.KYC_CODE_MISMATCH);

            assertThat(verification.getStatus()).isEqualTo(KycVerificationStatus.PENDING);
            verify(kycAttemptService).recordFailedAttempt(VERIFICATION_ID);
            verify(mockBankAccountRepository, never()).save(any());
        }

        @Test
        @DisplayName("실패: 인증 요청을 찾을 수 없음")
        void fail_notFound() {
            when(kycVerificationRepository.findByIdAndUserId(VERIFICATION_ID, USER_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> mockBankAccountService.verifyKyc(USER_ID, VERIFICATION_ID, CODE))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(MockBankAccountErrorCode.KYC_VERIFICATION_NOT_FOUND);
        }

        @Test
        @DisplayName("실패: 이미 인증이 완료된 요청")
        void fail_alreadyVerified() {
            KycVerification verification = newVerification();
            verification.markVerified();
            when(kycVerificationRepository.findByIdAndUserId(VERIFICATION_ID, USER_ID))
                    .thenReturn(Optional.of(verification));

            assertThatThrownBy(() -> mockBankAccountService.verifyKyc(USER_ID, VERIFICATION_ID, CODE))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(MockBankAccountErrorCode.KYC_ALREADY_VERIFIED);
        }

        @Test
        @DisplayName("실패: 인증 시간이 만료됨")
        void fail_expired() {
            KycVerification verification = KycVerification.create(
                    user, BANK_NAME, ACCOUNT_NUMBER, HOLDER_NAME, CODE,
                    LocalDateTime.now().minusSeconds(1)
            );
            when(kycVerificationRepository.findByIdAndUserId(VERIFICATION_ID, USER_ID))
                    .thenReturn(Optional.of(verification));

            assertThatThrownBy(() -> mockBankAccountService.verifyKyc(USER_ID, VERIFICATION_ID, CODE))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(MockBankAccountErrorCode.KYC_CODE_EXPIRED);
        }

        @Test
        @DisplayName("실패: 시도횟수를 초과함")
        void fail_attemptsExceeded() {
            KycVerification verification = newVerification();
            for (int i = 0; i < KycVerification.MAX_ATTEMPTS; i++) {
                verification.incrementAttemptAndGetRemaining();
            }
            when(kycVerificationRepository.findByIdAndUserId(VERIFICATION_ID, USER_ID))
                    .thenReturn(Optional.of(verification));

            assertThatThrownBy(() -> mockBankAccountService.verifyKyc(USER_ID, VERIFICATION_ID, CODE))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(MockBankAccountErrorCode.KYC_ATTEMPTS_EXCEEDED);

            verify(kycAttemptService, never()).recordFailedAttempt(anyLong());
        }
    }
}
