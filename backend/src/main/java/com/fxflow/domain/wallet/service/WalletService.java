package com.fxflow.domain.wallet.service;

import com.fxflow.domain.fxrate.service.FxRateService;
import com.fxflow.domain.ledger.entity.LedgerEntry;
import com.fxflow.domain.ledger.repository.LedgerEntryRepository;
import com.fxflow.domain.wallet.dto.response.TransactionHistoryResponse;
import com.fxflow.domain.wallet.dto.response.WalletBalanceResponse;
import com.fxflow.domain.wallet.dto.response.WalletResponse;
import com.fxflow.domain.wallet.entity.Wallet;
import com.fxflow.domain.wallet.errorcode.WalletErrorCode;
import com.fxflow.domain.wallet.repository.WalletRepository;
import com.fxflow.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final ExchangeService exchangeService;
    private final P2pTransferService p2pTransferService;
    private final FxRateService fxRateService;
    private final LedgerEntryRepository ledgerEntryRepository;

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

    public TransactionHistoryResponse getTransactionHistory(Long userId, String currency, LocalDate from, LocalDate to, Pageable pageable) {
        // userId + currency로 Wallet 조회
        List<Long> walletIds;
        if (currency != null) {
            Wallet wallet = walletRepository.findByUserIdAndCurrencyCode(userId, currency)
                    .orElseThrow(() -> new BusinessException(WalletErrorCode.WALLET_NOT_FOUND));
            walletIds = List.of(wallet.getId());
        } else {
            walletIds = walletRepository.findByUserId(userId).stream().map(Wallet::getId).toList();
        }

        if (walletIds.isEmpty()) {  // 사용자의 월렛이 없을 시 DB 조회하지 않고 바로 빈 페이지 반환
            return TransactionHistoryResponse.from(Page.empty(pageable));
        }

        LocalDateTime fromDateTime = from != null ? from.atStartOfDay() : null;
        LocalDateTime toDateTime = to != null ? to.atTime(23, 59, 59) : null;

        Page<LedgerEntry> entries = ledgerEntryRepository.findByWalletIdInAndFilters(
                walletIds, currency, fromDateTime, toDateTime, pageable
        );

        // DTO 변환
        return TransactionHistoryResponse.from(entries);
    }
}