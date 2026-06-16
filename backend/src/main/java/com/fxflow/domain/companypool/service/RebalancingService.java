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
import com.fxflow.domain.fxrate.service.FxRateService;
import com.fxflow.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class RebalancingService {

    private static final BigDecimal SPREAD = new BigDecimal("0.003");
    private final AtomicBoolean executing = new AtomicBoolean(false);

    private final CompanyPoolRepository companyPoolRepository;
    private final RebalancingRepository rebalancingRepository;
    private final FxRateService fxRateService;

    private record TradeAmounts(BigDecimal buyAmount, BigDecimal sellAmount, CappedBy cappedBy) {}

    // 충전·환급·해외송금 거래 커밋 후 자동 리밸런싱 트리거
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
        CompanyPool krwPool = findPool("KRW");
        CompanyPool usdPool = findPool("USD");

        checkBothBelowFloor(krwPool, usdPool);

        if (!krwPool.isBelowFloor() && !usdPool.isBelowFloor()) {
            log.debug("floor 미만 풀 없음. 리밸런싱 불필요.");
            return RebalancingExecuteRes.withinThreshold();
        }

        boolean buyingKrw = krwPool.isBelowFloor();
        CompanyPool buyPool  = buyingKrw ? krwPool : usdPool;
        CompanyPool sellPool = buyingKrw ? usdPool : krwPool;

        BigDecimal midRate     = fetchMidRate(triggerType);
        BigDecimal appliedRate = midRate.multiply(BigDecimal.ONE.add(SPREAD))
                .setScale(8, RoundingMode.HALF_UP);

        TradeAmounts amounts = calculateTradeAmounts(buyPool, sellPool, appliedRate, buyingKrw);

        BigDecimal buyBalanceBefore  = buyPool.getBalance();
        BigDecimal sellBalanceBefore = sellPool.getBalance();

        RebalancingStatus status = applyBalanceChanges(buyPool, sellPool, amounts, triggerType);

        RebalancingOrder order = RebalancingOrder.create(
                buyPool, sellPool,
                amounts.buyAmount(), amounts.sellAmount(),
                buyBalanceBefore, sellBalanceBefore,
                midRate, appliedRate,
                status, amounts.cappedBy(),
                triggerType, reason,
                UUID.randomUUID().toString()
        );
        rebalancingRepository.save(order);

        log.info("리밸런싱 완료. status={}, buy={}[{}], sell={}[{}], triggerType={}",
                status, amounts.buyAmount(), buyPool.getCurrencyCode(),
                amounts.sellAmount(), sellPool.getCurrencyCode(), triggerType);

        return RebalancingExecuteRes.from(order);
    }

    private void checkBothBelowFloor(CompanyPool krwPool, CompanyPool usdPool) {
        if (krwPool.isBelowFloor() && usdPool.isBelowFloor()) {
            log.error("[ALERT] 양 통화 모두 floor 미만 — 즉시 점검 필요. krwBalance={}, usdBalance={}",
                    krwPool.getBalance(), usdPool.getBalance());
            // TODO: 관리자 알림 발송 (이메일/Slack 등)
            throw new BusinessException(PoolErrorCode.BOTH_BELOW_FLOOR);
        }
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

    private RebalancingStatus applyBalanceChanges(CompanyPool buyPool, CompanyPool sellPool,
                                                  TradeAmounts amounts, TriggerType triggerType) {
        try {
            companyPoolRepository.increaseBalance(buyPool.getCurrencyCode(), amounts.buyAmount());
            int updated = companyPoolRepository.decreaseBalance(sellPool.getCurrencyCode(), amounts.sellAmount());
            if (updated == 0) {
                log.error("매도 풀 잔액 부족으로 감소 실패. sellCurrency={}, sellAmount={}",
                        sellPool.getCurrencyCode(), amounts.sellAmount());
                return RebalancingStatus.FAILED;
            }
            return RebalancingStatus.SUCCESS;
        } catch (Exception e) {
            log.error("리밸런싱 잔액 업데이트 오류. triggerType={}", triggerType, e);
            return RebalancingStatus.FAILED;
        }
    }

    private CompanyPool findPool(String currencyCode) {
        return companyPoolRepository.findByCurrencyCode(currencyCode)
                .orElseThrow(() -> new BusinessException(PoolErrorCode.POOL_NOT_FOUND));
    }

    private BigDecimal fetchMidRate(TriggerType triggerType) {
        try {
            return fxRateService.getRate("USD", "KRW");
        } catch (Exception e) {
            log.warn("환율 조회 실패. triggerType={}", triggerType, e);
            throw new BusinessException(PoolErrorCode.RATE_UNAVAILABLE, e);
        }
    }
}