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

    // 특정 유저의 삭제되지 않은 통화별 모의계좌를 조회하는 쿼리 메서드
    Optional<MockBankAccount> findFirstByUser_IdAndCurrencyCodeAndDeletedAtIsNull(
            Long userId,
            String currencyCode
    );

    Optional<MockBankAccount> findByAccountNumberAndCurrencyCodeAndDeletedAtIsNull(
            String accountNumber,
            String currencyCode
    );
}
