package com.fxflow.domain.companypool.service;

import com.fxflow.domain.companypool.dto.PoolChange;
import com.fxflow.domain.companypool.errorcode.PoolErrorCode;
import com.fxflow.domain.companypool.event.PoolChangedEvent;
import com.fxflow.domain.companypool.repository.CompanyPoolRepository;
import com.fxflow.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CompanyPoolService {

    private final CompanyPoolRepository companyPoolRepository;
    private final ApplicationEventPublisher eventPublisher;

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
}
