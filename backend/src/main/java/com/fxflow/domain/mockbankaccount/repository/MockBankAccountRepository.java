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

    /**
     * TRF-07 입금 확인 시 송금자 본인의 통화별 모의계좌를 조회한다.
     */
    Optional<MockBankAccount> findFirstByUser_IdAndCurrencyCodeAndDeletedAtIsNull(
            Long userId,
            String currencyCode
    );

    /**
     * TRF-08 외화 지급 시 송금자가 등록한 수취인 계좌번호와 통화로
     * 입금 대상 모의계좌를 조회한다.
     */
    Optional<MockBankAccount> findByAccountNumberAndCurrencyCodeAndDeletedAtIsNull(
            String accountNumber,
            String currencyCode
    );
}
