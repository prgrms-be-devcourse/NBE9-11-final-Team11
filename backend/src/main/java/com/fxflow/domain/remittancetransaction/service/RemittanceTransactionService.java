package com.fxflow.domain.remittancetransaction.service;

import com.fxflow.domain.remittancetransaction.dto.response.RemittanceLimitResponse;
import com.fxflow.domain.transactionlimit.entity.TransactionLimit;
import com.fxflow.domain.transactionlimit.enums.LimitTier;
import com.fxflow.domain.transactionlimit.enums.LimitType;
import com.fxflow.domain.transactionlimit.errorcode.TransactionLimitErrorCode;
import com.fxflow.domain.transactionlimit.repository.TransactionLimitRepository;
import com.fxflow.domain.userlimitusage.entity.UserAnnualUsage;
import com.fxflow.domain.userlimitusage.repository.UserAnnualUsageRepository;
import com.fxflow.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RemittanceTransactionService {

    private static final String USD = "USD";
    private static final LimitTier STANDARD_TIER = LimitTier.STANDARD;

    private final UserAnnualUsageRepository userAnnualUsageRepository;
    private final TransactionLimitRepository transactionLimitRepository;

    /**
     * TRF-03: 유저의 해외송금 잔여 한도 조회
     */
    public RemittanceLimitResponse getRemittanceLimit(Long userId) {
        int currentYear = LocalDate.now(ZoneId.of("Asia/Seoul")).getYear();

        BigDecimal currentYearTotalUsd = userAnnualUsageRepository
                .findByUserIdAndYear(userId, currentYear)
                .map(UserAnnualUsage::getAnnualUsedUsd)
                .orElse(BigDecimal.ZERO);

        BigDecimal maxPerTransactionUsd = getLimitAmount(LimitType.PER_REMITTANCE);
        BigDecimal maxPerYearUsd = getLimitAmount(LimitType.ANNUAL_REMITTANCE);

        return RemittanceLimitResponse.of(
                userId,
                maxPerTransactionUsd,
                maxPerYearUsd,
                currentYearTotalUsd
        );
    }

    private BigDecimal getLimitAmount(LimitType limitType) {
        return transactionLimitRepository
                .findByLimitTypeAndTierAndCurrencyCodeAndIsActiveTrue(
                        limitType,
                        STANDARD_TIER,
                        USD
                )
                .map(TransactionLimit::getLimitAmount)
                .orElseThrow(() -> new BusinessException(TransactionLimitErrorCode.LIMIT_POLICY_NOT_FOUND));
    }
}