package com.fxflow.domain.remittancetransaction.event;

import com.fxflow.domain.remittancetransaction.service.RemittancePayoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class RemittanceFundedEventListener {

    private final RemittancePayoutService remittancePayoutService;

    /**
     * 송금 입금 완료 트랜잭션이 커밋된 뒤 후속 송금 처리를 시작할 수 있는 지점이다.
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
        remittancePayoutService.processPayout(event.transferId());
    }
}
