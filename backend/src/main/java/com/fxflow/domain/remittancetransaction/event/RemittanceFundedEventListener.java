package com.fxflow.domain.remittancetransaction.event;

import com.fxflow.domain.remittancetransaction.service.RemittancePayoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class RemittanceFundedEventListener {

    private final RemittancePayoutService remittancePayoutService;

    /**
     * 송금 입금 완료 트랜잭션이 커밋된 뒤 후속 송금 처리를 비동기로 시작한다.
     * 후속 지급 실패가 입금 완료 API 응답으로 전파되지 않도록 이 지점에서 예외를 기록한다.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(RemittanceFundedEvent event) {
        log.info(
                "Remittance funded event published. transferId={}, userId={}, amountKrw={}, receiveCurrency={}, receiveAmount={}, fundedAt={}",
                event.transferId(),
                event.userId(),
                event.amountKrw(),
                event.receiveCurrency(),
                event.receiveAmount(),
                event.fundedAt()
        );
        try {
            remittancePayoutService.processPayout(event.transferId());
        } catch (RuntimeException e) {
            log.error("해외송금 후속 지급 처리 실패. transferId={}", event.transferId(), e);
        }
    }
}
