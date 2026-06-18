package com.fxflow.domain.remittancetransaction.dto.response;

import com.fxflow.global.util.CurrencyAmountFormatter;

import java.math.BigDecimal;

public record RemittanceLimitResponse(
        BigDecimal annualLimitUsd,
        BigDecimal usedUsd,
        BigDecimal remainingUsd,
        BigDecimal perTransferLimitUsd
) {

    private static final String USD = "USD";

    public static RemittanceLimitResponse of(
            BigDecimal perTransferLimitUsd,
            BigDecimal annualLimitUsd,
            BigDecimal usedUsd
    ) {
        BigDecimal remainingUsd = annualLimitUsd
                .subtract(usedUsd)
                .max(BigDecimal.ZERO);

        return new RemittanceLimitResponse(
                CurrencyAmountFormatter.format(annualLimitUsd, USD),
                CurrencyAmountFormatter.format(usedUsd, USD),
                CurrencyAmountFormatter.format(remainingUsd, USD),
                CurrencyAmountFormatter.format(perTransferLimitUsd, USD)
        );
    }
}
