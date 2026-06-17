package com.fxflow.domain.wallet.dto.request;

import java.math.BigDecimal;

public record ExchangeQuoteRequest(
        String fromCurrency,
        String toCurrency,
        BigDecimal amount
) {
}
