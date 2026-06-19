package com.fxflow.domain.remittancetransaction.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RemittancePayoutService {

    private final RemittancePayoutProcessor remittancePayoutProcessor;
    private final RemittanceRefundService remittanceRefundService;

    /**
     * 입금 완료(FUNDED) 이후 후속 지급 흐름을 조정한다.
     * 정상 지급과 실패 환불은 서로 다른 트랜잭션으로 분리해 rollback-only 전파를 막는다.
     */
    public void processPayout(Long transferId) {
        try {
            remittancePayoutProcessor.process(transferId);
        } catch (RuntimeException e) {
            log.warn("해외송금 지급 처리 실패. 환불을 시도합니다. transferId={}", transferId, e);
            remittanceRefundService.refundAfterPayoutFailure(transferId, e);
        }
    }
}
