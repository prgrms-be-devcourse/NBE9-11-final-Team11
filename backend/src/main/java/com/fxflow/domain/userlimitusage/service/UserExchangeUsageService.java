package com.fxflow.domain.userlimitusage.service;

import com.fxflow.domain.user.entity.User;
import com.fxflow.domain.user.errorcode.UserErrorCode;
import com.fxflow.domain.user.repository.UserRepository;
import com.fxflow.domain.userlimitusage.entity.UserExchangeAnnualUsage;
import com.fxflow.domain.userlimitusage.entity.UserExchangeDailyUsage;
import com.fxflow.domain.userlimitusage.repository.UserExchangeAnnualUsageRepository;
import com.fxflow.domain.userlimitusage.repository.UserExchangeDailyUsageRepository;
import com.fxflow.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Transactional
public class UserExchangeUsageService {

    private final UserExchangeDailyUsageRepository userExchangeDailyUsageRepository;
    private final UserExchangeAnnualUsageRepository userExchangeAnnualUsageRepository;
    private final UserRepository userRepository;

    public void addDailyExchange(Long userId, LocalDate usageDate, BigDecimal amount) {
        UserExchangeDailyUsage usage = userExchangeDailyUsageRepository.findByUserIdAndUsageDateForUpdate(
                userId, usageDate
        ).orElseGet(() -> {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
            return UserExchangeDailyUsage.create(user, usageDate);
        });
        usage.addExchange(amount);
        userExchangeDailyUsageRepository.save(usage);
    }

    public void addAnnualExchange(Long userId, Integer year, BigDecimal amount) {
        UserExchangeAnnualUsage usage = userExchangeAnnualUsageRepository.findByUserIdAndYearForUpdate(
                userId, year
        ).orElseGet(() -> {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
            return UserExchangeAnnualUsage.create(user, year);
        });
        usage.addExchange(amount);
        userExchangeAnnualUsageRepository.save(usage);
    }
}
