package com.fxflow.domain.mockbankaccount.repository;

import com.fxflow.domain.mockbankaccount.entity.MockBankAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * TRF-08 외화 지급 시 수취인 모의계좌 잔액을 갱신하므로
     * 같은 계좌에 동시에 입금되는 요청을 순서대로 처리하기 위해 비관적 락을 건다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT m
            FROM MockBankAccount m
            WHERE m.accountNumber = :accountNumber
              AND m.currencyCode = :currencyCode
              AND m.deletedAt IS NULL
            """)
    Optional<MockBankAccount> findByAccountNumberAndCurrencyCodeAndDeletedAtIsNullForUpdate(
            @Param("accountNumber") String accountNumber,
            @Param("currencyCode") String currencyCode
    );

    /**
     * TRF-08 실패 환불 시 송금자 모의계좌 잔액을 갱신하므로
     * 같은 계좌에 동시에 환불/입금되는 요청을 순서대로 처리하기 위해 비관적 락을 건다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT m
            FROM MockBankAccount m
            WHERE m.id = :id
              AND m.deletedAt IS NULL
            """)
    Optional<MockBankAccount> findByIdAndDeletedAtIsNullForUpdate(@Param("id") Long id);
}
