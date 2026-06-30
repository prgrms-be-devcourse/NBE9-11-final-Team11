package com.fxflow.domain.remittancetransaction.dto.response;

import com.fxflow.global.util.CurrencyAmountFormatter;

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

    private static final String KRW = "KRW";
    private static final String USD = "USD";

    public static RemittanceTransactionQuoteResponse of(
            BigDecimal sendAmountKrw,
            BigDecimal receiveAmountUsd,
            BigDecimal exchangeRate,
            BigDecimal fixedFee,
            BigDecimal percentFee,
            BigDecimal totalFee,
            String quoteId,
            LocalDateTime expiredAt
    ) {
        return new RemittanceTransactionQuoteResponse(
                CurrencyAmountFormatter.format(sendAmountKrw, KRW),
                CurrencyAmountFormatter.format(receiveAmountUsd, USD),
                exchangeRate,
                CurrencyAmountFormatter.format(fixedFee, KRW),
                CurrencyAmountFormatter.format(percentFee, KRW),
                CurrencyAmountFormatter.format(totalFee, KRW),
                quoteId,
                expiredAt
        );
    }
}
