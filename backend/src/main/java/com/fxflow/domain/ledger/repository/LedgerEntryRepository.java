package com.fxflow.domain.ledger.repository;

import com.fxflow.domain.ledger.entity.LedgerEntry;
import com.fxflow.domain.ledger.enums.LedgerEntryType;
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
            "AND (cast(:currency as string) IS NULL OR l.currencyCode = :currency) " +
            "AND (cast(:type as string) IS NULL OR l.entryType = :type) " +
            "AND (cast(:from as localdatetime) IS NULL OR l.createdAt >= :from) " +
            "AND (cast(:to as localdatetime) IS NULL OR l.createdAt <= :to)")
    Page<LedgerEntry> findByWalletIdInAndFilters(
            @Param("walletIds") List<Long> walletId,
            @Param("currency") String currency,
            @Param("type") LedgerEntryType type,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable
    );
}