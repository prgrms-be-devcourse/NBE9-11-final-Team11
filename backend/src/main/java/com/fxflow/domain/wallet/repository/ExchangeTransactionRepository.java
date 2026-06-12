package com.fxflow.domain.wallet.repository;

import com.fxflow.domain.wallet.entity.ExchangeTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExchangeTransactionRepository extends JpaRepository<ExchangeTransaction, Long> {

}