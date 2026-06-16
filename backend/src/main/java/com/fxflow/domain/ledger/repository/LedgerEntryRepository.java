package com.fxflow.domain.ledger.repository;

import com.fxflow.domain.ledger.entity.LedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {
    Page<LedgerEntry> findByWalletId(Long id, Pageable pageable);

    @Query("SELECT l FROM LedgerEntry l WHERE l.walletId IN :walletIds " +
            "AND (:currency IS NULL OR l.currencyCode = :currency) " +
            "AND (:from IS NULL OR l.createdAt >= :from) " +
            "AND (:to IS NULL OR l.createdAt <= :to) " +
            "ORDER BY l.createdAt DESC")
    Page<LedgerEntry> findByWalletIdInAndFilters(
            @Param("walletIds") List<Long> walletId,
            @Param("currency") String currency,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable
    );
}