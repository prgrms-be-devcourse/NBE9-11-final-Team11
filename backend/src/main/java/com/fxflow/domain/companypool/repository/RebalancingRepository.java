package com.fxflow.domain.companypool.repository;

import com.fxflow.domain.companypool.entity.RebalancingOrder;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RebalancingRepository extends JpaRepository<RebalancingOrder, Long> {
}