package com.fxflow.domain.transactionlimit.repository;

import com.fxflow.domain.transactionlimit.entity.TransactionLimit;
import com.fxflow.domain.transactionlimit.enums.LimitTier;
import com.fxflow.domain.transactionlimit.enums.LimitType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TransactionLimitRepository extends JpaRepository<TransactionLimit,Long> {
    Optional<TransactionLimit> findByLimitTypeAndTierAndCurrencyCodeAndIsActiveTrue(
            LimitType limitType,
            LimitTier tier,
            String currencyCode
    );
}
