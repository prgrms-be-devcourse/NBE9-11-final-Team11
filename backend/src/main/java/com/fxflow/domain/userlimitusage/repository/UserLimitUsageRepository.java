package com.fxflow.domain.userlimitusage.repository;

import com.fxflow.domain.userlimitusage.entity.UserLimitUsage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserLimitUsageRepository extends JpaRepository<UserLimitUsage,Long> {
    Optional<UserLimitUsage> findByUserIdAndYear(Long userId, Integer year);
}
