package com.fxflow.domain.wallet.repository;

import com.fxflow.domain.wallet.entity.ExchangeTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExchangeTransactionRepository extends JpaRepository<ExchangeTransaction, Long> {
    List<ExchangeTransaction> findAllByTransactionIdIn(List<String> transactionIds);
}