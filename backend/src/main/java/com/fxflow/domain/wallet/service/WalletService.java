package com.fxflow.domain.wallet.service;

import com.fxflow.domain.wallet.dto.response.WalletRes;
import com.fxflow.domain.wallet.entity.Wallet;
import com.fxflow.domain.wallet.errorcode.WalletErrorCode;
import com.fxflow.domain.wallet.repository.WalletRepository;
import com.fxflow.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final ExchangeService exchangeService;
    private final P2pTransferService p2pTransferService;

    private Wallet getWallet(){
        return walletRepository.findById(1L).orElseThrow(
                () -> new BusinessException(WalletErrorCode.WALLET_NOT_FOUND)
        );
    }

    public WalletRes getWallets() {
        return new WalletRes();
    }
}