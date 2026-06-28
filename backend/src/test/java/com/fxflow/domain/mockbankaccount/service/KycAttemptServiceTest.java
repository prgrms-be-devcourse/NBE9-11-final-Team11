package com.fxflow.domain.mockbankaccount.service;

import com.fxflow.domain.mockbankaccount.entity.KycVerification;
import com.fxflow.domain.mockbankaccount.errorcode.MockBankAccountErrorCode;
import com.fxflow.domain.mockbankaccount.repository.KycVerificationRepository;
import com.fxflow.domain.user.entity.User;
import com.fxflow.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KycAttemptServiceTest {

    @Mock
    private KycVerificationRepository kycVerificationRepository;

    @InjectMocks
    private KycAttemptService kycAttemptService;

    @Mock
    private User user;

    private static final Long VERIFICATION_ID = 1L;

    private KycVerification newVerification() {
        return KycVerification.create(
                user, "국민은행", "123456789012", "홍길동", "1234",
                LocalDateTime.now().plusMinutes(5)
        );
    }

    @Test
    @DisplayName("성공: 시도횟수를 1 증가시키고 저장한 뒤 남은 횟수를 반환한다")
    void recordFailedAttempt_success() {
        // given
        KycVerification verification = newVerification();
        when(kycVerificationRepository.findById(VERIFICATION_ID))
                .thenReturn(Optional.of(verification));

        // when
        int remaining = kycAttemptService.recordFailedAttempt(VERIFICATION_ID);

        // then
        assertThat(remaining).isEqualTo(KycVerification.MAX_ATTEMPTS - 1);
        assertThat(verification.getAttemptCount()).isEqualTo(1);
        verify(kycVerificationRepository).save(verification);
    }

    @Test
    @DisplayName("실패: 대상 인증 건이 없으면 KYC_VERIFICATION_NOT_FOUND")
    void recordFailedAttempt_notFound() {
        // given
        when(kycVerificationRepository.findById(VERIFICATION_ID))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> kycAttemptService.recordFailedAttempt(VERIFICATION_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(MockBankAccountErrorCode.KYC_VERIFICATION_NOT_FOUND);

        verify(kycVerificationRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
