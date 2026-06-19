package com.fxflow.domain.wallet.repository;

import com.fxflow.domain.wallet.entity.ExchangeTransaction;
import com.fxflow.domain.wallet.enums.ExchangeStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExchangeTransactionRepository extends JpaRepository<ExchangeTransaction, Long> {
    boolean existsByUser_IdAndStatus(Long userId, ExchangeStatus status);
}