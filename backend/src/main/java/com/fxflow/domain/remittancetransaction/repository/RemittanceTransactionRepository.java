package com.fxflow.domain.remittancetransaction.repository;

import com.fxflow.domain.remittancetransaction.entity.RemittanceTransaction;
import com.fxflow.domain.remittancetransaction.enums.TransferStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
     * 특정 유저의 해외송금 거래 이력을 최신순으로 페이지 조회한다.
     */
    Page<RemittanceTransaction> findByUserId(Long userId, Pageable pageable);

    /**
     * 특정 송금 거래가 로그인한 사용자의 거래인지 확인하며 조회한다.
     */
    Optional<RemittanceTransaction> findByIdAndUserId(Long id, Long userId);

    /**
     * 동일 Idempotency-Key 송금 주문을 조회한다.
     */
    Optional<RemittanceTransaction> findByIdempotencyKey(String idempotencyKey);

    /**
     * 특정 유저의 진행중인 거래가 있는지 조회한다.
     */
    boolean existsByUserIdAndStatusIn(Long userId, List<TransferStatus> statuses);
}
