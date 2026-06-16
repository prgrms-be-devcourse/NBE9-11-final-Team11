package com.fxflow.domain.remittancetransaction.validator;

import com.fxflow.domain.transactionlimit.validator.TransactionLimitValidator;
import com.fxflow.domain.user.entity.User;
import com.fxflow.domain.user.repository.UserRepository;
import com.fxflow.global.exception.BusinessException;
import com.fxflow.global.exception.GlobalErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
public class RemittanceValidator {

    private final UserRepository userRepository;
    private final TransactionLimitValidator transactionLimitValidator;

    /**
     * TRF-03: 해외송금 한도 검증
     * - 요청 금액 유효성 검증
     * - 건당 송금 한도 검증
     * - 연간 송금 한도 검증
     */
    public void validateLimits(Long userId, BigDecimal requestAmountUsd) {
        validatePositiveAmount(requestAmountUsd);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE));

        transactionLimitValidator.validatePerRemittance(user, requestAmountUsd);
        transactionLimitValidator.validateAnnualRemittance(user, requestAmountUsd);

        log.info("[송금 한도 검증 완료] userId={}, requestAmountUsd={}", userId, requestAmountUsd);
    }

    // 요청 금액 유효성 검증
    private void validatePositiveAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }
    }
}