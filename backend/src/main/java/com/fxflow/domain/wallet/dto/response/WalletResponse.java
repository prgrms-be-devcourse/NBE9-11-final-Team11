package com.fxflow.domain.wallet.dto.response;

import com.fxflow.domain.wallet.entity.Wallet;
import com.fxflow.global.util.CurrencyAmountFormatter;

import java.math.BigDecimal;

public record WalletResponse(
    String currency,
    BigDecimal balance
) {
    public static WalletResponse from(Wallet wallet) {
        return new WalletResponse(
                wallet.getCurrencyCode(),
                CurrencyAmountFormatter.format(wallet.getBalance(), wallet.getCurrencyCode())
        );
    }
}
