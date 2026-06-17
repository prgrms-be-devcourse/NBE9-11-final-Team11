package com.fxflow.domain.companypool.service;

import com.fxflow.domain.companypool.entity.CompanyPool;
import com.fxflow.domain.companypool.errorcode.PoolErrorCode;
import com.fxflow.domain.companypool.repository.CompanyPoolRepository;
import com.fxflow.domain.ledger.entity.LedgerEntry;
import com.fxflow.domain.ledger.enums.LedgerDirection;
import com.fxflow.domain.ledger.enums.LedgerEntryType;
import com.fxflow.domain.ledger.repository.LedgerEntryRepository;
import com.fxflow.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class CompanyPoolService {

    private final CompanyPoolRepository companyPoolRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    public CompanyPool getPoolByCurrency(String currencyCode) {
        return companyPoolRepository.findByCurrencyCode(currencyCode)
                .orElseThrow(() -> new BusinessException(PoolErrorCode.POOL_NOT_FOUND));
    }

    public CompanyPool deposit(String journalId, String currencyCode, BigDecimal amount) {
        CompanyPool pool = getPoolByCurrency(currencyCode);
        BigDecimal balanceBefore = pool.getBalance();
        BigDecimal balanceAfter = balanceBefore.add(amount);

        pool.increase(amount);
        companyPoolRepository.save(pool);

        LedgerEntry poolEntry = LedgerEntry.create(
                journalId,
                LedgerEntryType.CHARGE,
                LedgerDirection.CREDIT,
                null,
                null,
                pool.getId(),
                currencyCode,
                amount,
                balanceBefore,
                balanceAfter,
                null,
                null
        );
        ledgerEntryRepository.save(poolEntry);

        return pool;
    }

    public void withdraw(String journalId, String currencyCode, BigDecimal amount) {
        CompanyPool pool = getPoolByCurrency(currencyCode);
        BigDecimal balanceBefore = pool.getBalance();
        BigDecimal balanceAfter = balanceBefore.subtract(amount);
        if (balanceAfter.compareTo(BigDecimal.ZERO) < 0) {
            // todo: error
        }
        pool.decrease(amount);
        companyPoolRepository.save(pool);

        LedgerEntry poolEntry = LedgerEntry.create(
                journalId,
                LedgerEntryType.WITHDRAW,
                LedgerDirection.DEBIT,
                null,
                null,
                pool.getId(),
                currencyCode,
                amount,
                balanceBefore,
                balanceAfter,
                null,
                null
        );
        ledgerEntryRepository.save(poolEntry);
    }
}