package com.fxflow.domain.mockbankaccount.dto.response;

import com.fxflow.domain.mockbankaccount.entity.MockBankAccount;
import com.fxflow.domain.wallet.entity.Wallet;
import com.fxflow.global.entity.Currency;
import com.fxflow.global.util.CurrencyAmountFormatter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

public record MockBankLinkResponse(
        MockAccountInfo mockAccount,
        List<WalletInfo> wallets,
        LocalDateTime linkedAt
) {
    public record MockAccountInfo(
            String bankName,
            String accountNumber,
            String currency,
            BigDecimal balance
    ) {}

    public record WalletInfo(
            Long walletId,
            String currency,
            BigDecimal balance
    ) {}

    public static MockBankLinkResponse of(MockBankAccount account, List<Wallet> wallets) {
        List<WalletInfo> walletInfos = wallets.stream()
                .map(w -> new WalletInfo(w.getId(), w.getCurrencyCode(), CurrencyAmountFormatter.format(w.getBalance(), w.getCurrencyCode())))
                .toList();

        return new MockBankLinkResponse(
                new MockAccountInfo(
                        account.getBankName(),
                        account.getAccountNumber(),
                        account.getCurrencyCode(),
                        CurrencyAmountFormatter.format(account.getBalance(), account.getCurrencyCode())
                ),
                walletInfos,
                account.getCreatedAt()
        );
    }
}