package com.fxflow.domain.companypool.service;

import com.fxflow.domain.companypool.dto.PoolChange;
import com.fxflow.domain.companypool.dto.response.PoolDashboardRes;
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
import com.fxflow.global.fx.ExchangeRateProvider;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CompanyPoolService {

    private static final BigDecimal SPREAD = RebalancingService.SPREAD;

    private final CompanyPoolRepository companyPoolRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final ExchangeRateProvider exchangeRateProvider;

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

    @Transactional(readOnly = true)
    public PoolDashboardRes getDashboard() {
        // 환율 조회 실패 시 counterAmount=null로만 내려보내고 대시보드는 정상 반환
        BigDecimal appliedRate = exchangeRateProvider.getLatestRate("USD", "KRW")
                .map(snap -> snap.midRate()
                        .multiply(BigDecimal.ONE.add(SPREAD))
                        .setScale(8, RoundingMode.FLOOR))
                .orElse(null);

        CompanyPool krwPool = getPoolByCurrency("KRW");
        CompanyPool usdPool = getPoolByCurrency("USD");

        List<PoolDashboardRes.PoolStatusRes> pools = List.of(
                toPoolStatusRes(krwPool, usdPool, appliedRate),
                toPoolStatusRes(usdPool, krwPool, appliedRate)
        );
        return new PoolDashboardRes(OffsetDateTime.now(), pools);
    }

    private PoolDashboardRes.PoolStatusRes toPoolStatusRes(CompanyPool pool, CompanyPool otherPool, BigDecimal appliedRate) {
        String status = pool.isBelowFloor() ? "BELOW_FLOOR"
                : pool.isAboveCeiling() ? "ABOVE_CEILING"
                : "NORMAL";
        // target 대비 현재 잔액 비율 (0.0 ~ 1.0). 클라이언트에서 *100 하면 퍼센트
        BigDecimal utilizationRate = pool.getBalance()
                .divide(pool.getTargetBalance(), 4, RoundingMode.FLOOR);

        PoolDashboardRes.RecommendedAction recommendedAction = null;
        if (!"NORMAL".equals(status)) {
            BigDecimal amount = "BELOW_FLOOR".equals(status)
                    ? pool.shortageToTarget()
                    : pool.getBalance().subtract(pool.getTargetBalance());

            // BUY일 때 상대 풀의 floor 여유분으로 실제 체결 가능량을 cap
            // 상대 풀도 floor 미만이면 환전 불가 → recommendedAction=null
            if ("BELOW_FLOOR".equals(status)) {
                BigDecimal sellSurplus = otherPool.surplusAboveFloor();
                if (sellSurplus.compareTo(BigDecimal.ZERO) <= 0) {
                    return new PoolDashboardRes.PoolStatusRes(
                            pool.getCurrencyCode(), pool.getBalance(),
                            pool.getTargetBalance(), pool.getFloorBalance(), pool.getCeilingBalance(),
                            status, utilizationRate, null);
                }
                if (appliedRate != null) {
                    BigDecimal maxBuyable = "KRW".equals(pool.getCurrencyCode())
                            ? sellSurplus.multiply(appliedRate).setScale(2, RoundingMode.FLOOR)
                            : sellSurplus.divide(appliedRate, 2, RoundingMode.FLOOR);
                    if (maxBuyable.compareTo(amount) < 0) {
                        amount = maxBuyable;
                    }
                }
            }

            BigDecimal counterAmount = calculateCounterAmount(pool.getCurrencyCode(), amount, appliedRate);
            String type = "BELOW_FLOOR".equals(status) ? "BUY" : "SELL";
            recommendedAction = new PoolDashboardRes.RecommendedAction(type, amount, counterAmount);
        }

        return new PoolDashboardRes.PoolStatusRes(
                pool.getCurrencyCode(),
                pool.getBalance(),
                pool.getTargetBalance(),
                pool.getFloorBalance(),
                pool.getCeilingBalance(),
                status,
                utilizationRate,
                recommendedAction
        );
    }

    // KRW 액션 → 상대 통화 USD 매도량: amount / appliedRate
    // USD 액션 → 상대 통화 KRW 매도량: amount * appliedRate
    private BigDecimal calculateCounterAmount(String currencyCode, BigDecimal amount, BigDecimal appliedRate) {
        if (appliedRate == null) return null;
        return "KRW".equals(currencyCode)
                ? amount.divide(appliedRate, 2, RoundingMode.FLOOR)
                : amount.multiply(appliedRate).setScale(2, RoundingMode.FLOOR);
    }

    @Transactional
    public CompanyPool deposit(String journalId, String currencyCode, BigDecimal amount) {
        CompanyPool pool = companyPoolRepository.findByCurrencyCodeWithLock(currencyCode)
                .orElseThrow(() -> new BusinessException(PoolErrorCode.POOL_NOT_FOUND));
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
        eventPublisher.publishEvent(new PoolChangedEvent(this));

        return pool;
    }

    @Transactional
    public void withdraw(String journalId, String currencyCode, BigDecimal amount) {
        CompanyPool pool = companyPoolRepository.findByCurrencyCodeWithLock(currencyCode)
                .orElseThrow(() -> new BusinessException(PoolErrorCode.POOL_NOT_FOUND));
        BigDecimal balanceBefore = pool.getBalance();
        BigDecimal balanceAfter = balanceBefore.subtract(amount);
        if (balanceAfter.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(PoolErrorCode.POOL_INSUFFICIENT_BALANCE);
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
        eventPublisher.publishEvent(new PoolChangedEvent(this));
    }

    /**
     * TRF-07 가상계좌 입금 확인 후 회사 KRW 풀을 증가시킨다.
     * 지갑 충전이 아니므로 LedgerEntryType.TRANSFER와 REMITTANCE 참조로 원장을 남긴다.
     */
    @Transactional
    public CompanyPool depositForRemittance(String journalId, String currencyCode, BigDecimal amount, Long remittanceTransactionId) {
        CompanyPool pool = companyPoolRepository.findByCurrencyCodeWithLock(currencyCode)
                .orElseThrow(() -> new BusinessException(PoolErrorCode.POOL_NOT_FOUND));
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
                LedgerRefType.REMITTANCE,
                String.valueOf(remittanceTransactionId)
        );
        ledgerEntryRepository.save(poolEntry);
        eventPublisher.publishEvent(new PoolChangedEvent(this));

        return pool;
    }

    /**
     * 해외송금 지급/환불에서 회사 풀을 차감한다.
     * 지갑 출금이 아니므로 LedgerEntryType.TRANSFER와 REMITTANCE 참조로 원장을 남긴다.
     */
    @Transactional
    public CompanyPool withdrawForRemittance(String journalId, String currencyCode, BigDecimal amount, Long remittanceTransactionId) {
        CompanyPool pool = companyPoolRepository.findByCurrencyCodeWithLock(currencyCode)
                .orElseThrow(() -> new BusinessException(PoolErrorCode.POOL_NOT_FOUND));
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
                LedgerRefType.REMITTANCE,
                String.valueOf(remittanceTransactionId)
        );
        ledgerEntryRepository.save(poolEntry);
        eventPublisher.publishEvent(new PoolChangedEvent(this));

        return pool;
    }
}
