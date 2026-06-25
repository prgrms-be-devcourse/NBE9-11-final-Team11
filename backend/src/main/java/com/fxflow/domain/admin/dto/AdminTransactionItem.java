package com.fxflow.domain.admin.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminTransactionItem(
        Long id,
        String sourceType,    // LEDGER, REBALANCING
        String subType,       // CHARGE/WITHDRAW/EXCHANGE/TRANSFER/REMITTANCE or SUCCESS/FAILED/MANUAL_REQUIRED
        LocalDateTime createdAt,
        BigDecimal amount,
        String currencyCode,  // rebalancing은 null
        String journalId,     // rebalancing은 null
        String triggerType    // ledger는 null, rebalancing은 AUTO/MANUAL/SCHEDULER
) {
}
