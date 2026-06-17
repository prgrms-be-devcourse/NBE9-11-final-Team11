package com.fxflow.domain.remittancetransaction.dto.response;

import com.fxflow.domain.remittancetransaction.entity.Recipient;

public record RecipientResponse(
        Long recipientId,
        String name,
        String countryCode,
        String currencyCode,
        String bankName,
        String accountNumber
) {

    public static RecipientResponse from(Recipient recipient) {
        return new RecipientResponse(
                recipient.getId(),
                recipient.getName(),
                recipient.getCountryCode(),
                recipient.getCurrencyCode(),
                recipient.getBankName(),
                recipient.getAccountNumber()
        );
    }
}