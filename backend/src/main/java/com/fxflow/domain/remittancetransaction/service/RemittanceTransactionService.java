package com.fxflow.domain.remittancetransaction.service;

import com.fxflow.domain.remittancetransaction.dto.response.RemittanceLimitResponse;
import com.fxflow.domain.remittancetransaction.repository.RemittanceTransactionRepository;
import com.fxflow.domain.userlimitusage.entity.UserLimitUsage;
import com.fxflow.domain.userlimitusage.repository.UserLimitUsageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RemittanceTransactionService {

    private final RemittanceTransactionRepository remittanceTransactionRepository;
    private final UserLimitUsageRepository userLimitUsageRepository;

    /**
     * TRF-03: 유저의 해외송금 잔여 한도 조회
     */
    public RemittanceLimitResponse getRemittanceLimit(Long userId) {

        // UserLimitUsage 엔티티의 필드명 annualUsedUsd에 맞춰 getAnnualUsedUsd() 호출
        BigDecimal currentYearTotalUsd = userLimitUsageRepository.findById(userId)
                .map(UserLimitUsage::getAnnualUsedUsd)
                .orElse(BigDecimal.ZERO);

        BigDecimal maxPerYearUsd = new BigDecimal("100000.00");

        // 남은 연간 한도 계산
        BigDecimal availableYearUsd = maxPerYearUsd.subtract(currentYearTotalUsd);
        if (availableYearUsd.compareTo(BigDecimal.ZERO) < 0) {
            availableYearUsd = BigDecimal.ZERO;
        }

        return RemittanceLimitResponse.of(userId, currentYearTotalUsd, availableYearUsd);
    }
}