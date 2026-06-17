package com.fxflow.domain.remittancetransaction.repository;

import com.fxflow.domain.remittancetransaction.entity.VirtualAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VirtualAccountRepository extends JpaRepository<VirtualAccount, Long> {

    // 고유한 가상계좌 번호로 가상계좌 엔티티를 단건 조회하는 쿼리 메서드
    Optional<VirtualAccount> findByAccountNumber(String accountNumber);

    // 송금 거래 ID로 연결된 가상계좌를 조회하는 쿼리 메서드
    Optional<VirtualAccount> findByRemittanceTransactionId(Long remittanceTransactionId);
}