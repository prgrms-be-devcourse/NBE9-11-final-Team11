package com.fxflow.domain.ledger.entity;

import com.fxflow.domain.ledger.enums.LedgerDirection;
import com.fxflow.domain.ledger.enums.LedgerEntryType;
import com.fxflow.domain.ledger.enums.LedgerRefType;
import com.fxflow.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Getter
@Table(name = "ledger_entries")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LedgerEntry extends BaseEntity {

    @Column(name = "journal_id", nullable = false, length = 100)
    private String journalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 30)
    private LedgerEntryType entryType;

    @Enumerated(EnumType.STRING)
    @Column(name = "ledger_direction", nullable = false, length = 10)
    private LedgerDirection ledgerDirection;

    @Column(name = "wallet_id")
    private Long walletId;

    @Column(name = "mock_bank_account_id")
    private Long mockBankAccountId;

    @Column(name = "company_pool_id")
    private Long companyPoolId;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "amount", nullable = false, precision = 18, scale = 8)
    private BigDecimal amount;

    @Column(name = "balance_before", nullable = false, precision = 18, scale = 8)
    private BigDecimal balanceBefore;

    @Column(name = "balance_after", nullable = false, precision = 18, scale = 8)
    private BigDecimal balanceAfter;

    @Enumerated(EnumType.STRING)
    @Column(name = "ref_type", length = 50)
    private LedgerRefType refType;

    @Column(name = "ref_id", length = 50)
    private String refId;

    private LedgerEntry(String journalId, LedgerEntryType entryType, LedgerDirection ledgerDirection,
                        Long walletId, Long mockBankAccountId, Long companyPoolId, String currencyCode,
                        BigDecimal amount, BigDecimal balanceBefore, BigDecimal balanceAfter,
                        LedgerRefType refType, String refId){
        this.journalId = journalId;
        this.entryType = entryType;
        this.ledgerDirection = ledgerDirection;
        this.walletId = walletId;
        this.mockBankAccountId = mockBankAccountId;
        this.companyPoolId = companyPoolId;
        this.currencyCode = currencyCode;
        this.amount = amount;
        this.balanceBefore = balanceBefore;
        this.balanceAfter = balanceAfter;
        this.refType = refType;
        this.refId = refId;
    }

    public static LedgerEntry create(
            String journalId, LedgerEntryType entryType, LedgerDirection ledgerDirection,
            Long walletId, Long mockBankAccountId, Long companyPoolId, String currencyCode,
            BigDecimal amount, BigDecimal balanceBefore, BigDecimal balanceAfter,
            LedgerRefType refType, String refId
    ) {
        return new LedgerEntry(
                journalId, entryType, ledgerDirection, walletId, mockBankAccountId, companyPoolId, currencyCode, amount, balanceBefore, balanceAfter, refType, refId);
    }

    public static String generateJournalId() {
        return "JNL-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }
}