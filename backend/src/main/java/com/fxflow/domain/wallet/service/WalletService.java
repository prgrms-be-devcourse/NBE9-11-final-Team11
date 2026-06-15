package com.fxflow.domain.wallet.service;

import com.fxflow.domain.fxrate.service.FxRateService;
import com.fxflow.domain.wallet.dto.response.WalletBalanceResponse;
import com.fxflow.domain.wallet.dto.response.WalletResponse;
import com.fxflow.domain.wallet.entity.Wallet;
import com.fxflow.domain.wallet.errorcode.WalletErrorCode;
import com.fxflow.domain.wallet.repository.WalletRepository;
import com.fxflow.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final ExchangeService exchangeService;
    private final P2pTransferService p2pTransferService;
    private final FxRateService fxRateService;

    private Wallet getWallet(){
        return walletRepository.findById(1L).orElseThrow(
                () -> new BusinessException(WalletErrorCode.WALLET_NOT_FOUND)
        );
    }

    public WalletBalanceResponse getWalletBalance(Long userId) {
        List<Wallet> wallets = walletRepository.findByUserId(userId);
        List<WalletResponse> walletResponses = wallets.stream().map(WalletResponse::from).toList();
        BigDecimal krw = wallets.stream()
                .map(wallet -> {
                    BigDecimal rate = fxRateService.getRate(
                            wallet.getCurrencyCode(),
                            "KRW"
                    );
                    return wallet.getBalance().multiply(rate);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return WalletBalanceResponse.from(krw.setScale(0, java.math.RoundingMode.HALF_UP).longValue(), walletResponses);
    }
}