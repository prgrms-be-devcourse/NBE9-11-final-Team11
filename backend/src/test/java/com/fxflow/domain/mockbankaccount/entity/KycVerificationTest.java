package com.fxflow.domain.mockbankaccount.entity;

import com.fxflow.domain.mockbankaccount.enums.KycVerificationStatus;
import com.fxflow.domain.mockbankaccount.errorcode.MockBankAccountErrorCode;
import com.fxflow.domain.user.entity.User;
import com.fxflow.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class KycVerificationTest {

    @Mock
    private User user;

    private static final String BANK_NAME = "국민은행";
    private static final String ACCOUNT_NUMBER = "123456789012";
    private static final String HOLDER_NAME = "홍길동";
    private static final String CODE = "1234";

    private KycVerification create(LocalDateTime expiresAt) {
        return KycVerification.create(user, BANK_NAME, ACCOUNT_NUMBER, HOLDER_NAME, CODE, expiresAt);
    }

    @Nested
    @DisplayName("생성")
    class Create {

        @Test
        @DisplayName("성공: PENDING 상태, 시도횟수 0으로 생성된다")
        void success() {
            KycVerification verification = create(LocalDateTime.now().plusMinutes(5));

            assertThat(verification.getStatus()).isEqualTo(KycVerificationStatus.PENDING);
            assertThat(verification.getAttemptCount()).isZero();
            assertThat(verification.getBankName()).isEqualTo(BANK_NAME);
            assertThat(verification.getAccountNumber()).isEqualTo(ACCOUNT_NUMBER);
            assertThat(verification.getAccountHolderName()).isEqualTo(HOLDER_NAME);
        }

        @Test
        @DisplayName("입금자명은 'fxflow' + 코드 형식이다")
        void depositorName() {
            KycVerification verification = create(LocalDateTime.now().plusMinutes(5));

            assertThat(verification.depositorName()).isEqualTo("fxflow1234");
        }
    }

    @Nested
    @DisplayName("만료 여부 확인")
    class IsExpired {

        @Test
        @DisplayName("만료시각 이전이면 만료되지 않은 것으로 본다")
        void notExpired() {
            KycVerification verification = create(LocalDateTime.now().plusMinutes(5));

            assertThat(verification.isExpired(LocalDateTime.now())).isFalse();
        }

        @Test
        @DisplayName("만료시각이 지나면 만료된 것으로 본다")
        void expiredByTime() {
            KycVerification verification = create(LocalDateTime.now().minusSeconds(1));

            assertThat(verification.isExpired(LocalDateTime.now())).isTrue();
        }

        @Test
        @DisplayName("expire() 호출 시 EXPIRED 상태가 되어 만료된 것으로 본다")
        void expiredByStatus() {
            KycVerification verification = create(LocalDateTime.now().plusMinutes(5));
            verification.expire();

            assertThat(verification.getStatus()).isEqualTo(KycVerificationStatus.EXPIRED);
            assertThat(verification.isExpired(LocalDateTime.now())).isTrue();
        }

        @Test
        @DisplayName("이미 VERIFIED 상태면 expire()를 호출해도 상태가 바뀌지 않는다")
        void expireIgnoredWhenVerified() {
            KycVerification verification = create(LocalDateTime.now().plusMinutes(5));
            verification.markVerified();
            verification.expire();

            assertThat(verification.getStatus()).isEqualTo(KycVerificationStatus.VERIFIED);
        }
    }

    @Nested
    @DisplayName("검증 가능 여부 확인 (assertVerifiable)")
    class AssertVerifiable {

        @Test
        @DisplayName("성공: PENDING + 미만료 + 시도횟수 미초과면 예외가 발생하지 않는다")
        void success() {
            KycVerification verification = create(LocalDateTime.now().plusMinutes(5));

            assertThatCode(() -> verification.assertVerifiable(LocalDateTime.now()))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("실패: 이미 VERIFIED 상태면 KYC_ALREADY_VERIFIED")
        void fail_alreadyVerified() {
            KycVerification verification = create(LocalDateTime.now().plusMinutes(5));
            verification.markVerified();

            assertThatThrownBy(() -> verification.assertVerifiable(LocalDateTime.now()))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(MockBankAccountErrorCode.KYC_ALREADY_VERIFIED);
        }

        @Test
        @DisplayName("실패: 만료되었으면 KYC_CODE_EXPIRED")
        void fail_expired() {
            KycVerification verification = create(LocalDateTime.now().minusSeconds(1));

            assertThatThrownBy(() -> verification.assertVerifiable(LocalDateTime.now()))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(MockBankAccountErrorCode.KYC_CODE_EXPIRED);
        }

        @Test
        @DisplayName("실패: 시도횟수가 MAX_ATTEMPTS 이상이면 KYC_ATTEMPTS_EXCEEDED")
        void fail_attemptsExceeded() {
            KycVerification verification = create(LocalDateTime.now().plusMinutes(5));
            for (int i = 0; i < KycVerification.MAX_ATTEMPTS; i++) {
                verification.incrementAttemptAndGetRemaining();
            }

            assertThatThrownBy(() -> verification.assertVerifiable(LocalDateTime.now()))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(MockBankAccountErrorCode.KYC_ATTEMPTS_EXCEEDED);
        }
    }

    @Nested
    @DisplayName("코드 일치 확인 (matchesCode)")
    class MatchesCode {

        @Test
        @DisplayName("입력한 코드가 같으면 true")
        void match() {
            KycVerification verification = create(LocalDateTime.now().plusMinutes(5));

            assertThat(verification.matchesCode(CODE)).isTrue();
        }

        @Test
        @DisplayName("입력한 코드가 다르면 false")
        void mismatch() {
            KycVerification verification = create(LocalDateTime.now().plusMinutes(5));

            assertThat(verification.matchesCode("0000")).isFalse();
        }
    }

    @Nested
    @DisplayName("시도 횟수 증가 (incrementAttemptAndGetRemaining)")
    class IncrementAttempt {

        @Test
        @DisplayName("호출할 때마다 시도횟수가 1씩 증가하고 남은 횟수를 반환한다")
        void incrementsAndReturnsRemaining() {
            KycVerification verification = create(LocalDateTime.now().plusMinutes(5));

            int remainingAfterFirst = verification.incrementAttemptAndGetRemaining();
            int remainingAfterSecond = verification.incrementAttemptAndGetRemaining();

            assertThat(verification.getAttemptCount()).isEqualTo(2);
            assertThat(remainingAfterFirst).isEqualTo(KycVerification.MAX_ATTEMPTS - 1);
            assertThat(remainingAfterSecond).isEqualTo(KycVerification.MAX_ATTEMPTS - 2);
        }
    }

    @Nested
    @DisplayName("인증 완료 처리 (markVerified)")
    class MarkVerified {

        @Test
        @DisplayName("호출하면 상태가 VERIFIED로 바뀐다")
        void marksVerified() {
            KycVerification verification = create(LocalDateTime.now().plusMinutes(5));

            verification.markVerified();

            assertThat(verification.getStatus()).isEqualTo(KycVerificationStatus.VERIFIED);
        }
    }
}
