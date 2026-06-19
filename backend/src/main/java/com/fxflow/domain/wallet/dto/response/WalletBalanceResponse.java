package com.fxflow.domain.wallet.dto.response;

import java.util.List;

public record WalletBalanceResponse (
        Long totalKrw,
        List<WalletResponse> walletResponseList
) {
    public static WalletBalanceResponse from(Long totalKrw, List<WalletResponse> walletResponseList) {
        return new WalletBalanceResponse(
                totalKrw,
                walletResponseList);
    }
}
