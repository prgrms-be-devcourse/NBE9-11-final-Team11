package com.fxflow.domain.userlimitusage.repository;

import com.fxflow.domain.userlimitusage.entity.UserDailyUsage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface UserDailyUsageRepository extends JpaRepository<UserDailyUsage, Long> {

    Optional<UserDailyUsage> findByUserIdAndUsageDate(Long userId, LocalDate usageDate);
}
