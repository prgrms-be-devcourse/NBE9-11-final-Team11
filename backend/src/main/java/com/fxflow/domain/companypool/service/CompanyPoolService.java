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

    public CompanyPool getKrwPool() {
        return companyPoolRepository.findByCurrencyCode("KRW")
                .orElseThrow(() -> new BusinessException(PoolErrorCode.POOL_NOT_FOUND));
    }

    public CompanyPool deposit(String journalId, BigDecimal amount) {
        CompanyPool pool = getKrwPool();
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
                "KRW",
                amount,
                balanceBefore,
                balanceAfter,
                null
        );
        ledgerEntryRepository.save(poolEntry);

        return pool;
    }
}