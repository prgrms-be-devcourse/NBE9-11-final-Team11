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
        String triggerType,   // ledger는 null, rebalancing은 AUTO/MANUAL/SCHEDULER
        String direction,        // DEBIT, CREDIT — rebalancing은 null
        String accountRole,      // WALLET, BANK, KRW_POOL, USD_POOL — rebalancing은 null
        BigDecimal krwPoolChange, // KRW 풀 변화량 (양수=증가, 음수=감소, null=미변경)
        BigDecimal usdPoolChange  // USD 풀 변화량 (양수=증가, 음수=감소, null=미변경)
) {
}
