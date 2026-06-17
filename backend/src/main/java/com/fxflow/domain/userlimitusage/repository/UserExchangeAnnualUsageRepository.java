package com.fxflow.domain.userlimitusage.repository;

import com.fxflow.domain.userlimitusage.entity.UserExchangeAnnualUsage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserExchangeAnnualUsageRepository extends JpaRepository<UserExchangeAnnualUsage, Long> {

    Optional<UserExchangeAnnualUsage> findByUserIdAndYear(Long userId, Integer year);
}