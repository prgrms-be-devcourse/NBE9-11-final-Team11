package com.fxflow.domain.transactionlimit.validator;

import com.fxflow.domain.transactionlimit.entity.TransactionLimit;
import com.fxflow.domain.transactionlimit.enums.LimitTier;
import com.fxflow.domain.transactionlimit.enums.LimitType;
import com.fxflow.domain.transactionlimit.errorcode.TransactionLimitErrorCode;
import com.fxflow.domain.transactionlimit.repository.TransactionLimitRepository;
import com.fxflow.domain.user.entity.User;
import com.fxflow.domain.userlimitusage.entity.UserAnnualUsage;
import com.fxflow.domain.userlimitusage.entity.UserDailyUsage;
import com.fxflow.domain.userlimitusage.repository.UserAnnualUsageRepository;
import com.fxflow.domain.userlimitusage.repository.UserDailyUsageRepository;
import com.fxflow.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionLimitValidator {

    private final TransactionLimitRepository transactionLimitRepository;
    private final UserAnnualUsageRepository userAnnualUsageRepository;
    private final UserDailyUsageRepository userDailyUsageRepository;


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

        int currentYear = LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).getYear();
        BigDecimal usedAmount = userAnnualUsageRepository
                .findByUserIdAndYear(user.getId(), currentYear)
                .map(UserAnnualUsage::getAnnualUsedUsd)
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
    /**
     * - USD 잔액을 현재 환율로 원화 환산
     * - KRW 잔액 + USD 원화 환산액 합산하여 검증
     * - 현재는 KRW 잔액만 검증
     */
    public void validateWalletHolding(User user, BigDecimal newBalanceKrw) {
        log.info("[월렛 보유 한도 검증 시작] userId={}, 변경후잔액={}KRW", user.getId(), newBalanceKrw);

        if (newBalanceKrw.compareTo(user.getWalletLimitKrw()) > 0) {
            log.warn("[월렛 보유 한도 검증 실패] userId={}, 변경후잔액={}KRW, 한도={}KRW",
                    user.getId(), newBalanceKrw, user.getWalletLimitKrw());
            throw new BusinessException(TransactionLimitErrorCode.WALLET_HOLDING_LIMIT_EXCEEDED);
        }

        log.info("[월렛 보유 한도 검증 완료] userId={}, 변경후잔액={}KRW, 한도={}KRW",
                user.getId(), newBalanceKrw, user.getWalletLimitKrw());
    }

    // 4. 일일 입금 한도 검증
    public void validateDailyDeposit(User user, BigDecimal amountKrw) {
        log.info("[일일 입금 한도 검증 시작] userId={}, 요청액={}KRW", user.getId(), amountKrw);

        TransactionLimit limit = getLimit(LimitType.DAILY_DEPOSIT, user.getLimitTier(), "KRW");

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        BigDecimal usedAmount = userDailyUsageRepository
                .findByUserIdAndUsageDate(user.getId(), today)
                .map(UserDailyUsage::getDailyDepositUsed)
                .orElse(BigDecimal.ZERO);

        if (usedAmount.add(amountKrw).compareTo(limit.getLimitAmount()) > 0) {
            log.warn("[일일 입금 한도 검증 실패] userId={}, 누적액={}KRW, 요청액={}KRW, 한도={}KRW",
                    user.getId(), usedAmount, amountKrw, limit.getLimitAmount());
            throw new BusinessException(TransactionLimitErrorCode.DAILY_DEPOSIT_LIMIT_EXCEEDED);
        }

        log.info("[일일 입금 한도 검증 완료] userId={}, 누적액={}KRW, 요청액={}KRW, 한도={}KRW",
                user.getId(), usedAmount, amountKrw, limit.getLimitAmount());
    }

    // 5. 일일 출금 한도 검증
    public void validateDailyWithdrawal(User user, BigDecimal amountKrw) {
        log.info("[일일 출금 한도 검증 시작] userId={}, 요청액={}KRW", user.getId(), amountKrw);

        TransactionLimit limit = getLimit(LimitType.DAILY_WITHDRAWAL, user.getLimitTier(), "KRW");

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        BigDecimal usedAmount = userDailyUsageRepository
                .findByUserIdAndUsageDate(user.getId(), today)
                .map(UserDailyUsage::getDailyWithdrawalUsed)
                .orElse(BigDecimal.ZERO);

        if (usedAmount.add(amountKrw).compareTo(limit.getLimitAmount()) > 0) {
            log.warn("[일일 출금 한도 검증 실패] userId={}, 누적액={}KRW, 요청액={}KRW, 한도={}KRW",
                    user.getId(), usedAmount, amountKrw, limit.getLimitAmount());
            throw new BusinessException(TransactionLimitErrorCode.DAILY_WITHDRAWAL_LIMIT_EXCEEDED);
        }

        log.info("[일일 출금 한도 검증 완료] userId={}, 누적액={}KRW, 요청액={}KRW, 한도={}KRW",
                user.getId(), usedAmount, amountKrw, limit.getLimitAmount());
    }

    // 6. 1회 입금  한도 검증
    public void validatePerDeposit(User user, BigDecimal amountKrw) {
        log.info("[1회 입금 한도 검증 시작] userId={}, 요청액={}KRW", user.getId(), amountKrw);

        TransactionLimit limit = getLimit(LimitType.PER_DEPOSIT, user.getLimitTier(), "KRW");

        if (amountKrw.compareTo(limit.getLimitAmount()) > 0) {
            log.warn("[1회 입금 한도 검증 실패] userId={}, 요청액={}KRW, 한도={}KRW",
                    user.getId(), amountKrw, limit.getLimitAmount());
            throw new BusinessException(TransactionLimitErrorCode.PER_DEPOSIT_LIMIT_EXCEEDED);
        }

        log.info("[1회 입금 한도 검증 완료] userId={}, 요청액={}KRW, 한도={}KRW",
                user.getId(), amountKrw, limit.getLimitAmount());
    }

    //  7. 1회 출금 한도 검증
    public void validatePerWithdrawal(User user, BigDecimal amountKrw) {
        log.info("[1회 출금 한도 검증 시작] userId={}, 요청액={}KRW", user.getId(), amountKrw);

        TransactionLimit limit = getLimit(LimitType.PER_WITHDRAWAL, user.getLimitTier(), "KRW");

        if (amountKrw.compareTo(limit.getLimitAmount()) > 0) {
            log.warn("[1회 출금 한도 검증 실패] userId={}, 요청액={}KRW, 한도={}KRW",
                    user.getId(), amountKrw, limit.getLimitAmount());
            throw new BusinessException(TransactionLimitErrorCode.PER_WITHDRAWAL_LIMIT_EXCEEDED);
        }

        log.info("[1회 출금 한도 검증 완료] userId={}, 요청액={}KRW, 한도={}KRW",
                user.getId(), amountKrw, limit.getLimitAmount());
    }
}