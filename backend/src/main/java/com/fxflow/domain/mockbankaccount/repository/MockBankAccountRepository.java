package com.fxflow.domain.mockbankaccount.repository;

import com.fxflow.domain.mockbankaccount.entity.MockBankAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MockBankAccountRepository extends JpaRepository<MockBankAccount,Long> {
}
