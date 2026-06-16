package com.fxflow.domain.ledger.entity;

import com.fxflow.domain.ledger.enums.LedgerDirection;
import com.fxflow.domain.ledger.enums.LedgerEntryType;
import com.fxflow.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

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

    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "balance_before", nullable = false, precision = 18, scale = 2)
    private BigDecimal balanceBefore;

    @Column(name = "balance_after", nullable = false, precision = 18, scale = 2)
    private BigDecimal balanceAfter;

//    @Column(name = "ref_type", nullable = false, length = 50)
//    private String refType;
    // 이미 entry type이 CHARGE, WITHDRAW, EXCHANGE, TRANSFER 구분

    @Column(name = "ref_id", nullable = false, length = 50)
    private String refId;

    private LedgerEntry(String journalId, LedgerEntryType entryType, LedgerDirection ledgerDirection,
                        Long walletId, Long mockBankAccountId, Long companyPoolId, String currencyCode,
                        BigDecimal amount, BigDecimal balanceBefore, BigDecimal balanceAfter,
                        String refId){
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
    }

    public static LedgerEntry create(
            String journalId, LedgerEntryType entryType, LedgerDirection ledgerDirection,
            Long walletId, Long mockBankAccountId, Long companyPoolId, String currencyCode,
            BigDecimal amount, BigDecimal balanceBefore, BigDecimal balanceAfter,
            String refId
    ) {
        return new LedgerEntry(
                journalId, entryType, ledgerDirection, walletId, mockBankAccountId, companyPoolId, currencyCode, amount, balanceBefore, balanceAfter, refId);
    }
}