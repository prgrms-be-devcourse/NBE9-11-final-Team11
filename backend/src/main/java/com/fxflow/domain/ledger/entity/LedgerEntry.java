package com.fxflow.domain.ledger.entity;

import com.fxflow.domain.ledger.enums.LedgerDirection;
import com.fxflow.domain.ledger.enums.LedgerEntryType;
import com.fxflow.domain.mockbankaccount.entity.MockBankAccount;
import com.fxflow.domain.wallet.entity.Wallet;
import com.fxflow.global.entity.BaseEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "ledger_entries")
public class LedgerEntry extends BaseEntity {

    @Column(name = "journal_id", nullable = false, length = 100)
    private String journalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 30)
    private LedgerEntryType entryType;

    @Enumerated(EnumType.STRING)
    @Column(name = "ledger_direction", nullable = false, length = 10)
    private LedgerDirection ledgerDirection;

    @ManyToOne
    @JoinColumn(name = "wallet_id")
    private Wallet wallet;

    @ManyToOne
    @JoinColumn(name = "mock_bank_account_id")
    private MockBankAccount mockBankAccount;

    // todo: company pool FK
    // private CompanyPool companyPool;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "balance_before", nullable = false, precision = 18, scale = 2)
    private BigDecimal balanceBefore;

    @Column(name = "balance_after", nullable = false, precision = 18, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "ref_type", nullable = false, length = 50)
    private String refType;

    @Column(name = "ref_id", nullable = false, length = 50)
    private String refId;
}