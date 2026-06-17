package com.fxflow.domain.mockbankaccount.repository;

import com.fxflow.domain.mockbankaccount.entity.MockBankAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface MockBankAccountRepository extends JpaRepository<MockBankAccount,Long> {
    Optional<MockBankAccount> findByIdAndUserId(Long bankAccountId, Long userId);

    Optional<MockBankAccount> findByUserIdAndCurrencyCode(Long userId, String currencyCode);

    boolean existsByUserIdAndCurrencyCode(Long userId, String currencyCode);

    boolean existsByAccountNumber(String accountNumber);
}
