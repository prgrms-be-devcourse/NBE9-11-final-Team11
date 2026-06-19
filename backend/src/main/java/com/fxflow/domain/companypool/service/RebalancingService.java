package com.fxflow.domain.companypool.service;

import com.fxflow.domain.companypool.dto.response.RebalancingExecuteRes;
import com.fxflow.domain.companypool.entity.CompanyPool;
import com.fxflow.domain.companypool.entity.RebalancingOrder;
import com.fxflow.domain.companypool.enums.CappedBy;
import com.fxflow.domain.companypool.enums.RebalancingStatus;
import com.fxflow.domain.companypool.enums.TriggerType;
import com.fxflow.domain.companypool.errorcode.PoolErrorCode;
import com.fxflow.domain.companypool.event.PoolChangedEvent;
import com.fxflow.domain.companypool.repository.CompanyPoolRepository;
import com.fxflow.domain.companypool.repository.RebalancingRepository;
import com.fxflow.global.alert.AdminAlertService;
import com.fxflow.global.exception.BusinessException;
import com.fxflow.global.fx.ExchangeRateProvider;
import com.fxflow.global.fx.FxRateSnapshot;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Service
@RequiredArgsConstructor
public class RebalancingService {

    private static final BigDecimal SPREAD = new BigDecimal("0.003");
    private final AtomicBoolean executing = new AtomicBoolean(false);

    private final CompanyPoolRepository companyPoolRepository;
    private final RebalancingRepository rebalancingRepository;
    private final RebalancingAuditService auditService;
    private final ExchangeRateProvider exchangeRateProvider;
    private final AdminAlertService adminAlertService;

    private record TradeAmounts(BigDecimal buyAmount, BigDecimal sellAmount, CappedBy cappedBy) {}

    // 거래 커밋 후 자동 리밸런싱 트리거
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPoolChanged(PoolChangedEvent event) {
        try {
            doExecute(TriggerType.AUTO, null);
        } catch (BusinessException e) {
            log.warn("자동 리밸런싱 스킵. code={}, message={}", e.getErrorCode().getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("자동 리밸런싱 실패", e);
        }
    }

    // 수동(MANUAL) 또는 스케줄러(SCHEDULER) 진입점
    @Transactional
    public RebalancingExecuteRes execute(TriggerType triggerType, String reason) {
        return doExecute(triggerType, reason);
    }

    private RebalancingExecuteRes doExecute(TriggerType triggerType, String reason) {
        if (!executing.compareAndSet(false, true)) {
            throw new BusinessException(PoolErrorCode.REBALANCE_IN_PROGRESS);
        }
        try {
            return rebalance(triggerType, reason);
        } finally {
            executing.set(false);
        }
    }

    private RebalancingExecuteRes rebalance(TriggerType triggerType, String reason) {
        // PESSIMISTIC_WRITE 락: 계산~UPDATE 사이 다른 트랜잭션의 풀 수정 차단
        CompanyPool krwPool = findPool("KRW");
        CompanyPool usdPool = findPool("USD");

        if (isBothBelowFloor(krwPool, usdPool, triggerType, reason)) {
            return RebalancingExecuteRes.bothBelowFloor();
        }
        if (!krwPool.isBelowFloor() && !usdPool.isBelowFloor()) {
            log.debug("floor 미만 풀 없음. 리밸런싱 불필요.");
            return RebalancingExecuteRes.withinThreshold();
        }

        boolean buyingKrw    = krwPool.isBelowFloor();
        CompanyPool buyPool  = buyingKrw ? krwPool : usdPool;
        CompanyPool sellPool = buyingKrw ? usdPool : krwPool;

        BigDecimal midRate     = fetchMidRate(triggerType);
        BigDecimal appliedRate = applySpread(midRate);
        TradeAmounts amounts   = calculateTradeAmounts(buyPool, sellPool, appliedRate, buyingKrw);

        return executeAndRecord(buyPool, sellPool, amounts, midRate, appliedRate, triggerType, reason);
    }

    private RebalancingExecuteRes executeAndRecord(CompanyPool buyPool, CompanyPool sellPool,
                                                   TradeAmounts amounts, BigDecimal midRate,
                                                   BigDecimal appliedRate, TriggerType triggerType,
                                                   String reason) {
        BigDecimal buyBalanceBefore  = buyPool.getBalance();
        BigDecimal sellBalanceBefore = sellPool.getBalance();
        String idempotencyKey = UUID.randomUUID().toString();

        try {
            applyBalanceChanges(buyPool, sellPool, amounts);
        } catch (BusinessException e) {
            // 잔액 변경 실패 → 메인 트랜잭션 롤백 전에 RETRY_REQUIRED 기록 저장 (별도 트랜잭션)
            auditService.saveRetryRequired(
                    buyPool.getId(), sellPool.getId(),
                    amounts.buyAmount(), amounts.sellAmount(),
                    buyBalanceBefore, sellBalanceBefore,
                    midRate, appliedRate, amounts.cappedBy(), triggerType, reason, idempotencyKey);
            throw e;
        }

        RebalancingOrder order = RebalancingOrder.create(
                buyPool, sellPool, amounts.buyAmount(), amounts.sellAmount(),
                buyBalanceBefore, sellBalanceBefore, midRate, appliedRate,
                RebalancingStatus.SUCCESS, amounts.cappedBy(), triggerType, reason, idempotencyKey);
        rebalancingRepository.save(order);
        log.info("리밸런싱 완료. buy={}[{}], sell={}[{}], triggerType={}",
                amounts.buyAmount(), buyPool.getCurrencyCode(),
                amounts.sellAmount(), sellPool.getCurrencyCode(), triggerType);
        return RebalancingExecuteRes.from(order);
    }

    private BigDecimal applySpread(BigDecimal midRate) {
        return midRate.multiply(BigDecimal.ONE.add(SPREAD)).setScale(8, RoundingMode.HALF_UP);
    }

    // 케이스 4: 양쪽 모두 floor 미만 → 환전으로 조정 불가, 관리자 알림 + 미실행 반환
    private boolean isBothBelowFloor(CompanyPool krwPool, CompanyPool usdPool,
                                      TriggerType triggerType, String reason) {
        if (krwPool.isBelowFloor() && usdPool.isBelowFloor()) {
            adminAlertService.sendBothBelowFloorAlert(krwPool.getBalance(), usdPool.getBalance());
            auditService.saveManualRequired(triggerType, reason, UUID.randomUUID().toString());
            return true;
        }
        return false;
    }

    // 매입량 = min(target 대비 부족분량, 반대 통화 floor까지 여유분량)
    private TradeAmounts calculateTradeAmounts(CompanyPool buyPool, CompanyPool sellPool,
                                               BigDecimal appliedRate, boolean buyingKrw) {
        BigDecimal desiredBuyAmount = buyPool.shortageToTarget();
        BigDecimal maxBuyableFromSell = buyingKrw
                ? sellPool.surplusAboveFloor().multiply(appliedRate).setScale(2, RoundingMode.HALF_UP)
                : sellPool.surplusAboveFloor().divide(appliedRate, 2, RoundingMode.HALF_UP);

        CappedBy cappedBy = null;
        BigDecimal buyAmount;
        if (maxBuyableFromSell.compareTo(desiredBuyAmount) < 0) {
            buyAmount = maxBuyableFromSell;
            cappedBy  = buyingKrw ? CappedBy.USD_FLOOR : CappedBy.KRW_FLOOR;
            log.info("리밸런싱 부분 체결. cappedBy={}, buyAmount={}", cappedBy, buyAmount);
        } else {
            buyAmount = desiredBuyAmount;
        }

        BigDecimal sellAmount = buyingKrw
                ? buyAmount.divide(appliedRate, 2, RoundingMode.HALF_UP)
                : buyAmount.multiply(appliedRate).setScale(2, RoundingMode.HALF_UP);

        return new TradeAmounts(buyAmount, sellAmount, cappedBy);
    }

    private void applyBalanceChanges(CompanyPool buyPool, CompanyPool sellPool, TradeAmounts amounts) {
        companyPoolRepository.increaseBalance(buyPool.getCurrencyCode(), amounts.buyAmount());
        int updated = companyPoolRepository.decreaseBalance(sellPool.getCurrencyCode(), amounts.sellAmount());
        if (updated == 0) {
            // PESSIMISTIC_WRITE 락 보유 중이므로 정상적으로는 발생하지 않음
            // TODO: 락 없이 WHERE balance - sellAmount >= floorBalance 조건부 원자적 UPDATE로 개선 검토
            log.error("매도 풀 잔액 부족으로 감소 실패. sellCurrency={}, sellAmount={}",
                    sellPool.getCurrencyCode(), amounts.sellAmount());
            throw new BusinessException(PoolErrorCode.POOL_INSUFFICIENT_BALANCE);
        }
    }

    private CompanyPool findPool(String currencyCode) {
        return companyPoolRepository.findByCurrencyCodeWithLock(currencyCode)
                .orElseThrow(() -> new BusinessException(PoolErrorCode.POOL_NOT_FOUND));
    }

    private BigDecimal fetchMidRate(TriggerType triggerType) {
        return exchangeRateProvider.getLatestRate("USD", "KRW")
                .map(FxRateSnapshot::midRate)
                .orElseThrow(() -> {
                    log.warn("환율 조회 실패. triggerType={}", triggerType);
                    return new BusinessException(PoolErrorCode.RATE_UNAVAILABLE);
                });
    }
}
