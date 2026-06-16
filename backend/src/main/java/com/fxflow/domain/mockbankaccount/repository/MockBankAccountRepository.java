package com.fxflow.domain.mockbankaccount.repository;

import com.fxflow.domain.mockbankaccount.entity.MockBankAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MockBankAccountRepository extends JpaRepository<MockBankAccount,Long> {

    Optional<MockBankAccount> findByUserIdAndCurrencyCode(Long userId, String currencyCode);

    boolean existsByUserIdAndCurrencyCode(Long userId, String currencyCode);

    boolean existsByAccountNumber(String accountNumber);
}
