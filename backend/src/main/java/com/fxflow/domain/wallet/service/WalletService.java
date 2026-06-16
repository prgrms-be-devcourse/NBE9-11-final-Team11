package com.fxflow.domain.wallet.service;

import com.fxflow.domain.companypool.entity.CompanyPool;
import com.fxflow.domain.companypool.service.CompanyPoolService;
import com.fxflow.domain.fxrate.service.FxRateService;
import com.fxflow.domain.ledger.entity.LedgerEntry;
import com.fxflow.domain.ledger.enums.LedgerDirection;
import com.fxflow.domain.ledger.enums.LedgerEntryType;
import com.fxflow.domain.ledger.repository.LedgerEntryRepository;
import com.fxflow.domain.mockbankaccount.service.MockBankAccountService;
import com.fxflow.domain.wallet.dto.request.ChargeRequest;
import com.fxflow.domain.wallet.dto.request.WithdrawRequest;
import com.fxflow.domain.wallet.dto.response.TransactionHistoryResponse;
import com.fxflow.domain.wallet.dto.response.TransactionResponse;
import com.fxflow.domain.wallet.dto.response.WalletBalanceResponse;
import com.fxflow.domain.wallet.dto.response.WalletResponse;
import com.fxflow.domain.wallet.entity.Wallet;
import com.fxflow.domain.wallet.errorcode.WalletErrorCode;
import com.fxflow.domain.wallet.policy.WalletPolicy;
import com.fxflow.domain.wallet.repository.WalletRepository;
import com.fxflow.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final FxRateService fxRateService;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final MockBankAccountService mockBankAccountService;
    private final CompanyPoolService companyPoolService;


    private Wallet getWallet(Long userId, String currencyCode){
        return walletRepository.findByUserIdAndCurrencyCode(userId, currencyCode).orElseThrow(
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

    @Transactional
    public TransactionResponse charge(Long userId, ChargeRequest request) {
        Long bankAccountId = request.bankAccountId();

        BigDecimal amount = request.amount();
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(WalletErrorCode.INVALID_AMOUNT);
        }

        // check wallet balance
        Wallet wallet = getWallet(userId, "KRW");
        Long walletId = wallet.getId();
        BigDecimal balanceBefore = wallet.getBalance();
        BigDecimal balanceAfter = balanceBefore.add(amount);

        if (balanceAfter.compareTo(WalletPolicy.MAX_KRW_BALANCE) > 0) {
            throw new BusinessException(WalletErrorCode.WALLET_LIMIT_EXCEEDED);
        }

        String journalId = "JRN_" + UUID.randomUUID();

        // mock bank account debit
        mockBankAccountService.withdraw(journalId, walletId, bankAccountId, amount, "KRW");

        // wallet credit
        wallet.updateBalance(amount);
        walletRepository.save(wallet);

        CompanyPool pool = companyPoolService.deposit(journalId, "KRW", amount);
        Long companyPoolId = pool.getId();

        LedgerEntry walletEntry =
                LedgerEntry.create(
                        journalId,
                        LedgerEntryType.CHARGE,
                        LedgerDirection.CREDIT,
                        walletId,
                        null,
                        null,
                        "KRW",
                        amount,
                        balanceBefore,
                        balanceAfter,
                        null
                );
        ledgerEntryRepository.save(walletEntry);
        return TransactionResponse.from(walletEntry);
    }

    public TransactionResponse withdraw(Long userId, WithdrawRequest request) {
        return null;
    }
}