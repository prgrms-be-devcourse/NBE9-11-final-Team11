package com.fxflow.domain.wallet.dto.response;

import java.math.BigDecimal;

public record WalletProfitResponse(
        BigDecimal realizedProfit,
        BigDecimal unrealizedProfit,
        BigDecimal totalProfit,
        BigDecimal currentRate
) {
    public static WalletProfitResponse of(BigDecimal realizedProfit, BigDecimal unrealizedProfit, BigDecimal currentRate) {
        return new WalletProfitResponse(
                realizedProfit,
                unrealizedProfit,
                realizedProfit.add(unrealizedProfit),
                currentRate
        );
    }
}
