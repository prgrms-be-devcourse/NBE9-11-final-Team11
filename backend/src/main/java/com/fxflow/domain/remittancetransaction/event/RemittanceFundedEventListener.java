package com.fxflow.domain.remittancetransaction.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class RemittanceFundedEventListener {

    /**
     * 송금 입금 완료 트랜잭션이 커밋된 뒤 후속 송금 처리를 시작할 수 있는 지점이다.
     * 현재는 MVP 단계라 로그만 남기고, 추후 TRF-08 외화 지급 처리 또는 Kafka 발행으로 교체한다.
     */
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
    }
}