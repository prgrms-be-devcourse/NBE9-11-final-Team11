package com.fxflow.domain.mockbankaccount.dto.response;

import com.fxflow.domain.mockbankaccount.entity.MockBankAccount;
import com.fxflow.global.util.CurrencyAmountFormatter;

import java.math.BigDecimal;

public record MockBankAccountResponse(
        String bankName,
        String accountNumber,
        String currency,
        BigDecimal balance
) {
    public static MockBankAccountResponse from(MockBankAccount account) {
        return new MockBankAccountResponse(
                account.getBankName(),
                account.getAccountNumber(),
                account.getCurrencyCode(),
                CurrencyAmountFormatter.format(account.getBalance(), account.getCurrencyCode())
        );
    }
}
