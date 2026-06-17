package com.fxflow.domain.remittancetransaction.repository;

import com.fxflow.domain.remittancetransaction.entity.RemittanceTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RemittanceTransactionRepository extends JpaRepository<RemittanceTransaction, Long> {

    /**
     * 특정 유저의 해외송금 거래 이력을 최신순으로 조회한다.
     */
    List<RemittanceTransaction> findByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * 특정 송금 거래가 로그인한 사용자의 거래인지 확인하며 조회한다.
     */
    Optional<RemittanceTransaction> findByIdAndUserId(Long id, Long userId);
}