package com.fxflow.domain.wallet.repository;

import com.fxflow.domain.wallet.entity.ExchangeTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExchangeTransactionRepository extends JpaRepository<ExchangeTransaction, Long> {
    List<ExchangeTransaction> findAllByTransactionIdIn(List<String> transactionIds);

    // 단건 조회 — transactionId 는 unique 제약이라 항상 0/1건
    Optional<ExchangeTransaction> findByTransactionId(String transactionId);
}