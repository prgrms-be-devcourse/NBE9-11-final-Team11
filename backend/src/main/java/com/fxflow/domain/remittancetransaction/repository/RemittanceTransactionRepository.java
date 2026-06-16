package com.fxflow.domain.remittancetransaction.repository;

import com.fxflow.domain.remittancetransaction.entity.RemittanceTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RemittanceTransactionRepository extends JpaRepository<RemittanceTransaction, Long> {

    // 특정 유저의 해외송금 거래 이력을 최신순으로 일괄 조회하는 쿼리 메서드
    List<RemittanceTransaction> findByUserIdOrderByCreatedAtDesc(Long userId);
}