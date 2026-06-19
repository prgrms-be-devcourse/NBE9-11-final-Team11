package com.fxflow.domain.wallet.dto.response;

import com.fxflow.global.util.CurrencyAmountFormatter;

import java.math.BigDecimal;

public record P2pTransferResponse(
        String transactionId,
        String recipientEmail,
        BigDecimal amount,
        String currency,
        BigDecimal senderBalanceAfter
) {
    public static P2pTransferResponse create(String transferId, String recipientEmail, BigDecimal amount, String currency, BigDecimal senderBalanceAfter) {
        return new P2pTransferResponse(
                transferId,
                recipientEmail,
                CurrencyAmountFormatter.format(amount, currency),
                currency,
                CurrencyAmountFormatter.format(senderBalanceAfter, currency)
        );
    }
}
