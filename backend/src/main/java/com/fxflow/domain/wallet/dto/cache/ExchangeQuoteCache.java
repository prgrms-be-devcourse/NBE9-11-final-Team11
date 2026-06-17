package com.fxflow.domain.wallet.dto.cache;

import java.io.Serializable;
import java.math.BigDecimal;

public record ExchangeQuoteCache(
        Long userId,
        String fromCurrency,
        String toCurrency,
        BigDecimal fromAmount,
        BigDecimal toAmount,
        BigDecimal appliedRate,
        BigDecimal feeAmount,
        BigDecimal totalAmount
) implements Serializable {}
