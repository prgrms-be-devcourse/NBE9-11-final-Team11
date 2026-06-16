package com.fxflow.domain.mockbankaccount.dto.response;

import com.fxflow.domain.mockbankaccount.entity.MockBankAccount;
import com.fxflow.domain.wallet.entity.Wallet;

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
                .map(w -> new WalletInfo(w.getId(), w.getCurrencyCode(), formatBalance(w.getBalance(), w.getCurrencyCode())))
                .toList();

        return new MockBankLinkResponse(
                new MockAccountInfo(
                        account.getBankName(),
                        account.getAccountNumber(),
                        account.getCurrencyCode(),
                        formatBalance(account.getBalance(), account.getCurrencyCode())
                ),
                walletInfos,
                account.getCreatedAt()
        );
    }

    /**
     * KRW: 소수점 없이 (정수)
     * USD: 소수점 2자리
     */
    private static BigDecimal formatBalance(BigDecimal balance, String currencyCode) {
        if ("KRW".equals(currencyCode)) {
            return balance.setScale(0, RoundingMode.DOWN);
        }
        return balance.setScale(2, RoundingMode.DOWN);
    }
}