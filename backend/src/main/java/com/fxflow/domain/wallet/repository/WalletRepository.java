package com.fxflow.domain.wallet.repository;

import com.fxflow.domain.wallet.entity.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface WalletRepository extends JpaRepository<Wallet, Long> {

    List<Wallet> findByUserId(Long userId);

    Optional<Wallet> findByUserIdAndCurrencyCode(Long userId, String currency);

    boolean existsByUserIdAndCurrencyCode(Long userId, String currency);

    // 동시성 문제 테스트용 메소드
    @Modifying
    @Query("UPDATE Wallet w SET w.balance = w.balance - :amount WHERE w.user.id = :userId AND w.currencyCode = 'KRW' AND w.balance >= :amount")
    int withdrawAtomic(Long userId, BigDecimal amount);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.user.id = :userId AND w.currencyCode = :currency")
    Optional<Wallet> findByUserIdAndCurrencyCodeWithLock(Long userId, String currency);

}