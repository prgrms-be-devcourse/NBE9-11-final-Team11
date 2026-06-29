package com.fxflow.domain.remittancetransaction.scheduler;

import com.fxflow.domain.remittancetransaction.service.RemittanceTransactionService;
import com.fxflow.global.util.KstClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RemittanceExpirationScheduler {

    private final RemittanceTransactionService remittanceTransactionService;

    /**
     * 입금 기한이 지난 해외송금 주문을 주기적으로 취소한다.
     * 주문마다 스케줄을 만들지 않고, 일정 주기마다 만료 대상을 한 번에 찾는다.
     */
    @Scheduled(fixedDelayString = "${remittance.expiration-scheduler.fixed-delay-ms:60000}")
    public void expirePendingTransfers() {
        int expiredCount = remittanceTransactionService.expirePendingTransfers(KstClock.now());

        if (expiredCount > 0) {
            log.info("입금 기한 만료 해외송금 주문 자동 취소 완료. count={}", expiredCount);
        }
    }
}
