package com.fxflow.domain.remittancetransaction.validator;

import com.fxflow.domain.userlimitusage.repository.UserLimitUsageRepository;
import com.fxflow.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class RemittanceValidatorTest {

    @Mock
    private UserLimitUsageRepository userLimitUsageRepository;

    @InjectMocks
    private RemittanceValidator remittanceValidator;

    @Test
    @DisplayName("송금액이 건당 한도($5,000)를 초과하면 예외가 발생한다")
    void validateLimits_ExceedTransactionLimit() {
        // given
        Long userId = 1L;
        BigDecimal requestAmount = new BigDecimal("5001.00");

        // when & then
        assertThrows(BusinessException.class, () ->
                remittanceValidator.validateLimits(userId, requestAmount)
        );
    }
}