package com.fxflow.domain.ledger.repository;

import com.fxflow.domain.ledger.entity.LedgerEntry;
import com.fxflow.domain.ledger.enums.LedgerDirection;
import com.fxflow.domain.ledger.enums.LedgerEntryType;
import com.fxflow.domain.ledger.enums.LedgerRefType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {
    Page<LedgerEntry> findByWalletId(Long id, Pageable pageable);

    @Query("""
            SELECT l
            FROM LedgerEntry l
            WHERE l.refType = :refType
              AND l.refId = :journalId
            ORDER BY l.createdAt ASC
            """)
    List<LedgerEntry> findRemittanceEntriesByJournalId(
            @Param("refType") LedgerRefType refType,
            @Param("journalId") String journalId
    );

    /**
     * 공통 거래내역 조회용 쿼리다.
     * 지갑 거래는 walletId로 찾고, 해외송금은 송금자 모의계좌의 REMITTANCE/DEBIT LedgerEntry만 대표 내역으로 포함한다.
     */
    @Query("""
            SELECT l
            FROM LedgerEntry l
            WHERE (
                l.walletId IN :walletIds
                OR (
                    l.refType = :remittanceRefType
                    AND l.ledgerDirection = :debitDirection
                    AND l.mockBankAccountId = :mockBankAccountId
                )
            )
            AND (cast(:currency as string) IS NULL OR l.currencyCode = :currency)
            AND (cast(:type as string) IS NULL OR l.entryType = :type)
            AND (cast(:from as localdatetime) IS NULL OR l.createdAt >= :from)
            AND (cast(:to as localdatetime) IS NULL OR l.createdAt <= :to)
            """)
    Page<LedgerEntry> findUnifiedTransactionHistory(
            @Param("walletIds") List<Long> walletIds,
            @Param("mockBankAccountId") Long mockBankAccountId,
            @Param("remittanceRefType") LedgerRefType remittanceRefType,
            @Param("debitDirection") LedgerDirection debitDirection,
            @Param("currency") String currency,
            @Param("type") LedgerEntryType type,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable
    );

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
