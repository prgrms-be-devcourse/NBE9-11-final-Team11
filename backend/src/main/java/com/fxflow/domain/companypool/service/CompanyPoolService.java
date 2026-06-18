package com.fxflow.domain.companypool.service;

import com.fxflow.domain.companypool.dto.PoolChange;
import com.fxflow.domain.companypool.entity.CompanyPool;
import com.fxflow.domain.companypool.errorcode.PoolErrorCode;
import com.fxflow.domain.companypool.event.PoolChangedEvent;
import com.fxflow.domain.companypool.repository.CompanyPoolRepository;
import com.fxflow.domain.ledger.entity.LedgerEntry;
import com.fxflow.domain.ledger.enums.LedgerDirection;
import com.fxflow.domain.ledger.enums.LedgerEntryType;
import com.fxflow.domain.ledger.enums.LedgerRefType;
import com.fxflow.domain.ledger.repository.LedgerEntryRepository;
import com.fxflow.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CompanyPoolService {

    private final CompanyPoolRepository companyPoolRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final LedgerEntryRepository ledgerEntryRepository;

    /**
     * 충전·환급·해외송금이 호출하는 풀 갱신 인터페이스
     * 모든 변경을 하나의 트랜잭션으로 처리하고 이벤트를 한 번만 발행
     */
    @Transactional
    public void apply(List<PoolChange> changes) {
        for (PoolChange change : changes) {
            if (change.isIncrease()) {
                companyPoolRepository.increaseBalance(change.currencyCode(), change.amount());
            } else {
                int updated = companyPoolRepository.decreaseBalance(change.currencyCode(), change.amount());
                if (updated == 0) {
                    throw new BusinessException(PoolErrorCode.POOL_INSUFFICIENT_BALANCE);
                }
            }
        }
        eventPublisher.publishEvent(new PoolChangedEvent(this));
    }

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

    @Transactional
    public CompanyPool depositForRemittance(String journalId, String currencyCode, BigDecimal amount, Long remittanceTransactionId) {
        CompanyPool pool = getPoolByCurrency(currencyCode);
        BigDecimal balanceBefore = pool.getBalance();
        BigDecimal balanceAfter = balanceBefore.add(amount);

        pool.increase(amount);
        companyPoolRepository.save(pool);

        LedgerEntry poolEntry = LedgerEntry.create(
                journalId,
                LedgerEntryType.TRANSFER,
                LedgerDirection.CREDIT,
                null,
                null,
                pool.getId(),
                currencyCode,
                amount,
                balanceBefore,
                balanceAfter,
                LedgerRefType.REMITTANCE.name(),
                String.valueOf(remittanceTransactionId)
        );
        ledgerEntryRepository.save(poolEntry);
        eventPublisher.publishEvent(new PoolChangedEvent(this));

        return pool;
    }

    @Transactional
    public CompanyPool withdrawForRemittance(String journalId, String currencyCode, BigDecimal amount, Long remittanceTransactionId) {
        CompanyPool pool = getPoolByCurrency(currencyCode);
        BigDecimal balanceBefore = pool.getBalance();
        BigDecimal balanceAfter = balanceBefore.subtract(amount);

        if (balanceAfter.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(PoolErrorCode.POOL_INSUFFICIENT_BALANCE);
        }

        pool.decrease(amount);
        companyPoolRepository.save(pool);

        LedgerEntry poolEntry = LedgerEntry.create(
                journalId,
                LedgerEntryType.TRANSFER,
                LedgerDirection.DEBIT,
                null,
                null,
                pool.getId(),
                currencyCode,
                amount,
                balanceBefore,
                balanceAfter,
                LedgerRefType.REMITTANCE.name(),
                String.valueOf(remittanceTransactionId)
        );
        ledgerEntryRepository.save(poolEntry);
        eventPublisher.publishEvent(new PoolChangedEvent(this));

        return pool;
    }

    @Transactional
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
