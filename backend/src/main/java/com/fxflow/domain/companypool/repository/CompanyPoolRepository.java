package com.fxflow.domain.companypool.repository;

import com.fxflow.domain.companypool.entity.CompanyPool;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;

public interface CompanyPoolRepository extends JpaRepository<CompanyPool, Long> {

    Optional<CompanyPool> findByCurrencyCode(String currencyCode);

    // 리밸런싱 : read~update 사이 다른 트랜잭션의 수정 차단
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM CompanyPool p WHERE p.currencyCode = :code")
    Optional<CompanyPool> findByCurrencyCodeWithLock(@Param("code") String currencyCode);

    @Modifying
    @Query("UPDATE CompanyPool p SET p.balance = p.balance + :amount WHERE p.currencyCode = :code")
    int increaseBalance(@Param("code") String currencyCode, @Param("amount") BigDecimal amount);

    // 잔액 감소 - 반환값: 1(성공), 0(잔액 부족으로 실패)
    @Modifying
    @Query("UPDATE CompanyPool p SET p.balance = p.balance - :amount WHERE p.currencyCode = :code AND p.balance >= :amount")
    int decreaseBalance(@Param("code") String currencyCode, @Param("amount") BigDecimal amount);
}
