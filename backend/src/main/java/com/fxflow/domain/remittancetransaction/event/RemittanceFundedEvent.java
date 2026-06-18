package com.fxflow.domain.remittancetransaction.event;

import com.fxflow.domain.remittancetransaction.entity.RemittanceTransaction;
import com.fxflow.domain.remittancetransaction.entity.VirtualAccount;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RemittanceFundedEvent(
        Long transferId,
        Long userId,
        BigDecimal amountKrw,
        String receiveCurrency,
        BigDecimal receiveAmount,
        LocalDateTime fundedAt
) {

    /**
     * 송금 입금 완료 후 후속 처리에 필요한 이벤트 정보를 생성한다.
     */
    public static RemittanceFundedEvent of(
            RemittanceTransaction remittanceTransaction,
            VirtualAccount virtualAccount
    ) {
        return new RemittanceFundedEvent(
                remittanceTransaction.getId(),
                remittanceTransaction.getUserId(),
                virtualAccount.getExpectedAmount(),
                remittanceTransaction.getReceiveCurrency(),
                remittanceTransaction.getReceiveAmount(),
                virtualAccount.getPaidAt()
        );
    }
}