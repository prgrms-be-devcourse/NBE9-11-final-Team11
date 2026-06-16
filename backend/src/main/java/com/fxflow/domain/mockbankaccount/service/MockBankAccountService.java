package com.fxflow.domain.mockbankaccount.service;

import com.fxflow.domain.ledger.entity.LedgerEntry;
import com.fxflow.domain.ledger.enums.LedgerDirection;
import com.fxflow.domain.ledger.enums.LedgerEntryType;
import com.fxflow.domain.ledger.repository.LedgerEntryRepository;
import com.fxflow.domain.mockbankaccount.entity.MockBankAccount;
import com.fxflow.domain.mockbankaccount.errorcode.MockBankAccountErrorCode;
import com.fxflow.domain.mockbankaccount.repository.MockBankAccountRepository;
import com.fxflow.global.exception.BusinessException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class MockBankAccountService {
    private final MockBankAccountRepository mockBankAccountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    public MockBankAccount findById(Long bankAccountId) {
        return mockBankAccountRepository.findById(bankAccountId).orElseThrow(
                () -> new IllegalArgumentException("Bank account not found") // todo: exception
        );
    }

    @Transactional
    public void withdraw(Long userId, String journalId, Long bankAccountId, BigDecimal amount, String currencyCode) {
        MockBankAccount bankAccount = mockBankAccountRepository.findByIdAndUserId(bankAccountId, userId)
                .orElseThrow(() -> new BusinessException(MockBankAccountErrorCode.MOCK_ACCOUNT_NOT_FOUND));

        BigDecimal balanceBefore = bankAccount.getBalance();
        BigDecimal balanceAfter = balanceBefore.subtract(amount);
        if (balanceAfter.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(MockBankAccountErrorCode.MOCK_ACCOUNT_INSUFFICIENT_BALANCE);
        }
        bankAccount.withdraw(amount);
        LedgerEntry entry = LedgerEntry.create(
                journalId,
                LedgerEntryType.CHARGE,
                LedgerDirection.DEBIT,
                null,
                bankAccountId,
                null,
                currencyCode,
                amount,
                balanceBefore,
                balanceAfter,
                null
        );
        ledgerEntryRepository.save(entry);
        mockBankAccountRepository.save(bankAccount);
    }

    @Transactional
    public void deposit(Long userId, String journalId, Long bankAccountId, BigDecimal amount, String currencyCode) {
        MockBankAccount bankAccount = mockBankAccountRepository.findByIdAndUserId(bankAccountId, userId)
                .orElseThrow(() -> new BusinessException(MockBankAccountErrorCode.MOCK_ACCOUNT_NOT_FOUND));

        BigDecimal balanceBefore = bankAccount.getBalance();
        BigDecimal balanceAfter = balanceBefore.add(amount);

        bankAccount.deposit(amount);

        LedgerEntry entry = LedgerEntry.create(
                journalId,
                LedgerEntryType.WITHDRAW,
                LedgerDirection.CREDIT,
                null,
                bankAccountId,
                null,
                currencyCode,
                amount,
                balanceBefore,
                balanceAfter,
                null
        );

        ledgerEntryRepository.save(entry);
        mockBankAccountRepository.save(bankAccount);
    }
}
