package com.fxflow.domain.userlimitusage.repository;

import com.fxflow.domain.userlimitusage.entity.UserLimitUsage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserLimitUsageRepository extends JpaRepository<UserLimitUsage,Long> {
}
