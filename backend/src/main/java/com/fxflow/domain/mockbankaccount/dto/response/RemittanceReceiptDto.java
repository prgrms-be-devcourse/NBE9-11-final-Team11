// backend/src/main/java/com/fxflow/domain/mockbankaccount/dto/response/RemittanceReceiptDto.java
package com.fxflow.domain.mockbankaccount.dto.response;

import com.fxflow.global.util.CurrencyAmountFormatter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RemittanceReceiptDto(
        Long transactionId,
        String senderName,
        BigDecimal sendAmount,
        BigDecimal receiveAmount,
        BigDecimal exchangeRate,
        LocalDateTime createdAt
) {
    // 팩토리 메서드 도입 및 포매터 적용
    public static RemittanceReceiptDto of(
            Long transactionId, String senderName, BigDecimal sendAmount,
            BigDecimal receiveAmount, BigDecimal exchangeRate, LocalDateTime createdAt
    ) {
        return new RemittanceReceiptDto(
                transactionId,
                senderName,
                CurrencyAmountFormatter.format(sendAmount, "KRW"),
                CurrencyAmountFormatter.format(receiveAmount, "USD"),
                exchangeRate,
                createdAt
        );
    }
}