package com.fxflow.domain.userlimitusage.repository;

import com.fxflow.domain.userlimitusage.entity.UserExchangeDailyUsage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface UserExchangeDailyUsageRepository extends JpaRepository<UserExchangeDailyUsage, Long> {

    Optional<UserExchangeDailyUsage> findByUserIdAndUsageDate(Long userId, LocalDate usageDate);
}