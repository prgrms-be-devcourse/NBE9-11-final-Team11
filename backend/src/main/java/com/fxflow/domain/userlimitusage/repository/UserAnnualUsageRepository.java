package com.fxflow.domain.userlimitusage.repository;

import com.fxflow.domain.userlimitusage.entity.UserAnnualUsage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserAnnualUsageRepository extends JpaRepository<UserAnnualUsage, Long> {
    Optional<UserAnnualUsage> findByUserIdAndYear(Long userId, Integer year);
}
