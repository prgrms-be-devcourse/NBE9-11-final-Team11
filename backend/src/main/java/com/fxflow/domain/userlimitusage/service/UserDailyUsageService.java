package com.fxflow.domain.userlimitusage.service;

import com.fxflow.domain.userlimitusage.entity.UserDailyUsage;
import com.fxflow.domain.userlimitusage.errorcode.UserLimitUsageErrorCode;
import com.fxflow.domain.userlimitusage.repository.UserDailyUsageRepository;
import com.fxflow.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class UserDailyUsageService {
    private final UserDailyUsageRepository userDailyUsageRepository;

    public void addDeposit(Long userId, LocalDate usageDate, BigDecimal amount) {
        UserDailyUsage usage = userDailyUsageRepository.findByUserIdAndUsageDate(
                userId,usageDate
        ).orElseThrow(
                () -> new BusinessException(UserLimitUsageErrorCode.DAILY_USAGE_NOT_FOUND)
        );
        usage.addDeposit(amount);
        userDailyUsageRepository.save(usage);
    }

    public void addWithdrawal(Long userId, LocalDate usageDate, BigDecimal amount) {
        UserDailyUsage usage = userDailyUsageRepository.findByUserIdAndUsageDate(
                userId,usageDate
        ).orElseThrow(
                () -> new BusinessException(UserLimitUsageErrorCode.DAILY_USAGE_NOT_FOUND)
        );
        usage.addWithdrawal(amount);
        userDailyUsageRepository.save(usage);
    }
}
