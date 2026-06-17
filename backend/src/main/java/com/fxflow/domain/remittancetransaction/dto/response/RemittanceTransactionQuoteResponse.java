package com.fxflow.domain.remittancetransaction.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RemittanceTransactionQuoteResponse(
        BigDecimal sendAmountKrw,
        BigDecimal receiveAmountUsd,
        BigDecimal exchangeRate,
        BigDecimal fixedFee,
        BigDecimal percentFee,
        BigDecimal totalFee,
        String quoteId,
        LocalDateTime expiredAt
) {
}