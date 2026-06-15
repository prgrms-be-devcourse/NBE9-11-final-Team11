package com.fxflow.domain.transactionlimit.validator;

import com.fxflow.domain.transactionlimit.entity.TransactionLimit;
import com.fxflow.domain.transactionlimit.enums.LimitTier;
import com.fxflow.domain.transactionlimit.enums.LimitType;
import com.fxflow.domain.transactionlimit.errorcode.TransactionLimitErrorCode;
import com.fxflow.domain.transactionlimit.repository.TransactionLimitRepository;
import com.fxflow.domain.user.entity.User;
import com.fxflow.domain.userlimitusage.entity.UserLimitUsage;
import com.fxflow.domain.userlimitusage.repository.UserLimitUsageRepository;
import com.fxflow.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionLimitValidator {

    private final TransactionLimitRepository transactionLimitRepository;
    private final UserLimitUsageRepository userLimitUsageRepository;

    //공통 한도정책 조회
    private TransactionLimit getLimit(LimitType limitType, LimitTier tier, String currencyCode) {
        return transactionLimitRepository
                .findByLimitTypeAndTierAndCurrencyCodeAndIsActiveTrue(
                        limitType, tier, currencyCode
                )
                .orElseThrow(() -> {
                    log.error("[한도 정책 없음] limitType={}, tier={}, currencyCode={}",
                            limitType, tier, currencyCode);
                    return new BusinessException(TransactionLimitErrorCode.LIMIT_POLICY_NOT_FOUND);
                });
    }
    // 1. 건당 송금 한도 검증
    public void validatePerRemittance(User user, BigDecimal amountUsd) {
        log.info("[건당 송금 한도 검증 시작] userId={}, 요청액={}USD", user.getId(), amountUsd);

        TransactionLimit limit = getLimit(LimitType.PER_REMITTANCE, LimitTier.STANDARD, "USD");

        if (amountUsd.compareTo(limit.getLimitAmount()) > 0) {
            log.warn("[건당 송금 한도 검증 실패] userId={}, 요청액={}USD, 한도={}USD",
                    user.getId(), amountUsd, limit.getLimitAmount());
            throw new BusinessException(TransactionLimitErrorCode.PER_REMITTANCE_LIMIT_EXCEEDED);
        }

        log.info("[건당 송금 한도 검증 완료] userId={}, 요청액={}USD, 한도={}USD",
                user.getId(), amountUsd, limit.getLimitAmount());
    }

    // 2. 연간 송금 한도 검증
    public void validateAnnualRemittance(User user, BigDecimal amountUsd) {
        log.info("[연간 송금 한도 검증 시작] userId={}, 요청액={}USD", user.getId(), amountUsd);

        TransactionLimit limit = getLimit(LimitType.ANNUAL_REMITTANCE, LimitTier.STANDARD, "USD");

        int currentYear = LocalDate.now().getYear();
        BigDecimal usedAmount = userLimitUsageRepository
                .findByUserIdAndYear(user.getId(), currentYear)
                .map(UserLimitUsage::getAnnualUsedUsd)
                .orElse(BigDecimal.ZERO);

        if (usedAmount.add(amountUsd).compareTo(limit.getLimitAmount()) > 0) {
            log.warn("[연간 송금 한도 검증 실패] userId={}, 누적액={}USD, 요청액={}USD, 한도={}USD",
                    user.getId(), usedAmount, amountUsd, limit.getLimitAmount());
            throw new BusinessException(TransactionLimitErrorCode.ANNUAL_REMITTANCE_LIMIT_EXCEEDED);
        }

        log.info("[연간 송금 한도 검증 완료] userId={}, 누적액={}USD, 요청액={}USD, 한도={}USD",
                user.getId(), usedAmount, amountUsd, limit.getLimitAmount());
    }

    // 3. 월렛 보유 한도 검증
    public void validateWalletHolding(User user, BigDecimal newBalanceKrw) { }

    // 4. 일일 입금 한도 검증
    public void validateDailyDeposit(User user, BigDecimal amountKrw) { }

    // 5. 일일 출금 한도 검증
    public void validateDailyWithdrawal(User user, BigDecimal amountKrw) { }
}