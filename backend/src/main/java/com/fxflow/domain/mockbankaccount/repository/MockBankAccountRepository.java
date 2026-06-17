package com.fxflow.domain.mockbankaccount.repository;

import com.fxflow.domain.mockbankaccount.entity.MockBankAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface MockBankAccountRepository extends JpaRepository<MockBankAccount,Long> {
    Optional<MockBankAccount> findByIdAndUserId(Long bankAccountId, Long userId);

    // 해외송금 입금 확인에 사용할 송금인 유저의 활성 KRW 모의계좌를 조회한다.
    Optional<MockBankAccount> findFirstByUser_IdAndCurrencyCodeAndDeletedAtIsNull(
            Long userId,
            String currencyCode
    );
}
