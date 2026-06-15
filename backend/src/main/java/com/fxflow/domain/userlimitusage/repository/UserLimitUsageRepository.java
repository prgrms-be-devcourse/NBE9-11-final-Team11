package com.fxflow.domain.userlimitusage.repository;

import com.fxflow.domain.userlimitusage.entity.UserLimitUsage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface UserLimitUsageRepository extends JpaRepository<UserLimitUsage,Long> {
    //연간 누적액 조회
    Optional<UserLimitUsage> findByUserIdAndYear(Long userId, Integer year);
    // 일별 누적액 조회
    Optional<UserLimitUsage> findByUserIdAndUsageDate(Long userId, LocalDate usageDate);
}
