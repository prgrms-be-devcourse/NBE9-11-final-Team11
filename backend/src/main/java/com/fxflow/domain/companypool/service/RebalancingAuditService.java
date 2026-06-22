package com.fxflow.domain.companypool.service;

import com.fxflow.domain.companypool.entity.CompanyPool;
import com.fxflow.domain.companypool.entity.RebalancingOrder;
import com.fxflow.domain.companypool.enums.CappedBy;
import com.fxflow.domain.companypool.enums.RebalancingStatus;
import com.fxflow.domain.companypool.enums.TriggerType;
import com.fxflow.domain.companypool.errorcode.PoolErrorCode;
import com.fxflow.domain.companypool.repository.CompanyPoolRepository;
import com.fxflow.domain.companypool.repository.RebalancingRepository;
import com.fxflow.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class RebalancingAuditService {

    private final CompanyPoolRepository companyPoolRepository;
    private final RebalancingRepository rebalancingRepository;

    // 메인 트랜잭션이 롤백돼도 기록은 독립적으로 커밋됨
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveFailed(Long buyPoolId, Long sellPoolId,
                                   BigDecimal buyAmount, BigDecimal sellAmount,
                                   BigDecimal buyBalanceBefore, BigDecimal sellBalanceBefore,
                                   BigDecimal midRate, BigDecimal appliedRate,
                                   CappedBy cappedBy, TriggerType triggerType,
                                   String reason, String idempotencyKey) {
        CompanyPool buyPool = companyPoolRepository.findById(buyPoolId)
                .orElseThrow(() -> new BusinessException(PoolErrorCode.POOL_NOT_FOUND));
        CompanyPool sellPool = companyPoolRepository.findById(sellPoolId)
                .orElseThrow(() -> new BusinessException(PoolErrorCode.POOL_NOT_FOUND));
        rebalancingRepository.save(RebalancingOrder.create(
                buyPool, sellPool, buyAmount, sellAmount,
                buyBalanceBefore, sellBalanceBefore,
                midRate, appliedRate,
                RebalancingStatus.FAILED, cappedBy,
                triggerType, reason, idempotencyKey
        ));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveManualRequired(TriggerType triggerType, String reason, String idempotencyKey) {
        rebalancingRepository.save(
                RebalancingOrder.createAlert(triggerType, reason, idempotencyKey)
        );
    }
}