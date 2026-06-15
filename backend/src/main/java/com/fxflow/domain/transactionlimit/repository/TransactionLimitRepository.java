package com.fxflow.domain.transactionlimit.repository;

import com.fxflow.domain.transactionlimit.entity.TransactionLimit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionLimitRepository extends JpaRepository<TransactionLimit,Long> {
}
