package com.fxflow.domain.userlimitusage.service;

import com.fxflow.domain.user.entity.User;
import com.fxflow.domain.user.errorcode.UserErrorCode;
import com.fxflow.domain.user.repository.UserRepository;
import com.fxflow.domain.userlimitusage.entity.UserDailyUsage;
import com.fxflow.domain.userlimitusage.repository.UserDailyUsageRepository;
import com.fxflow.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Transactional
public class UserDailyUsageService {
    private final UserDailyUsageRepository userDailyUsageRepository;
    private final UserRepository userRepository;

    public void addDeposit(Long userId, LocalDate usageDate, BigDecimal amount) {
        UserDailyUsage usage = userDailyUsageRepository.findByUserIdAndUsageDateForUpdate(
                userId,usageDate
        ).orElseGet( () -> {
            User user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
            return UserDailyUsage.create(user, usageDate);
        });
        usage.addDeposit(amount);
        userDailyUsageRepository.save(usage);
    }

    public void addWithdrawal(Long userId, LocalDate usageDate, BigDecimal amount) {
        UserDailyUsage usage = userDailyUsageRepository.findByUserIdAndUsageDateForUpdate(
                userId,usageDate
        ).orElseGet( () -> {
                    User user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
                    return UserDailyUsage.create(user, usageDate);
                });
        usage.addWithdrawal(amount);
        userDailyUsageRepository.save(usage);
    }
}
