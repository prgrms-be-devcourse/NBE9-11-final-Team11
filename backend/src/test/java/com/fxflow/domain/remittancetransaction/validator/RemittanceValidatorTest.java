package com.fxflow.domain.remittancetransaction.validator;

import com.fxflow.domain.transactionlimit.validator.TransactionLimitValidator;
import com.fxflow.domain.user.entity.User;
import com.fxflow.domain.user.repository.UserRepository;
import com.fxflow.global.exception.BusinessException;
import com.fxflow.global.exception.GlobalErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RemittanceValidatorTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TransactionLimitValidator transactionLimitValidator;

    @Mock
    private User user;

    @InjectMocks
    private RemittanceValidator remittanceValidator;

    @Test
    @DisplayName("성공: 요청 금액이 유효하면 건당/연간 송금 한도 검증을 호출한다")
    void validateLimits_success() {
        // given
        Long userId = 1L;
        BigDecimal requestAmountUsd = new BigDecimal("3000.00");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // when & then
        assertThatCode(() -> remittanceValidator.validateLimits(userId, requestAmountUsd))
                .doesNotThrowAnyException();

        verify(transactionLimitValidator).validatePerRemittance(user, requestAmountUsd);
        verify(transactionLimitValidator).validateAnnualRemittance(user, requestAmountUsd);
    }

    @Test
    @DisplayName("성공: 요청 금액이 건당 한도와 같은 금액이면 검증을 호출한다")
    void validateLimits_success_equalPerRemittanceLimit() {
        // given
        Long userId = 1L;
        BigDecimal requestAmountUsd = new BigDecimal("5000.00");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // when & then
        assertThatCode(() -> remittanceValidator.validateLimits(userId, requestAmountUsd))
                .doesNotThrowAnyException();

        verify(transactionLimitValidator).validatePerRemittance(user, requestAmountUsd);
        verify(transactionLimitValidator).validateAnnualRemittance(user, requestAmountUsd);
    }

    @Test
    @DisplayName("실패: 요청 금액이 null이면 예외가 발생한다")
    void validateLimits_fail_nullAmount() {
        // given
        Long userId = 1L;

        // when & then
        assertThatThrownBy(() -> remittanceValidator.validateLimits(userId, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(GlobalErrorCode.INVALID_INPUT_VALUE);

        verifyNoInteractions(userRepository);
        verifyNoInteractions(transactionLimitValidator);
    }

    @Test
    @DisplayName("실패: 요청 금액이 0이면 예외가 발생한다")
    void validateLimits_fail_zeroAmount() {
        // given
        Long userId = 1L;
        BigDecimal requestAmountUsd = BigDecimal.ZERO;

        // when & then
        assertThatThrownBy(() -> remittanceValidator.validateLimits(userId, requestAmountUsd))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(GlobalErrorCode.INVALID_INPUT_VALUE);

        verifyNoInteractions(userRepository);
        verifyNoInteractions(transactionLimitValidator);
    }

    @Test
    @DisplayName("실패: 요청 금액이 음수이면 예외가 발생한다")
    void validateLimits_fail_negativeAmount() {
        // given
        Long userId = 1L;
        BigDecimal requestAmountUsd = new BigDecimal("-1.00");

        // when & then
        assertThatThrownBy(() -> remittanceValidator.validateLimits(userId, requestAmountUsd))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(GlobalErrorCode.INVALID_INPUT_VALUE);

        verifyNoInteractions(userRepository);
        verifyNoInteractions(transactionLimitValidator);
    }

    @Test
    @DisplayName("실패: 유저가 존재하지 않으면 예외가 발생한다")
    void validateLimits_fail_userNotFound() {
        // given
        Long userId = 999L;
        BigDecimal requestAmountUsd = new BigDecimal("3000.00");

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> remittanceValidator.validateLimits(userId, requestAmountUsd))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(GlobalErrorCode.INVALID_INPUT_VALUE);

        verifyNoInteractions(transactionLimitValidator);
    }
}