package com.fxflow.domain.wallet.service;

import com.fxflow.domain.companypool.service.CompanyPoolService;
import com.fxflow.domain.fxrate.service.FxRateService;
import com.fxflow.domain.ledger.entity.LedgerEntry;
import com.fxflow.domain.ledger.enums.LedgerDirection;
import com.fxflow.domain.ledger.enums.LedgerEntryType;
import com.fxflow.domain.ledger.repository.LedgerEntryRepository;
import com.fxflow.domain.mockbankaccount.service.MockBankAccountService;
import com.fxflow.domain.transactionlimit.validator.TransactionLimitValidator;
import com.fxflow.domain.user.entity.User;
import com.fxflow.domain.user.service.UserService;
import com.fxflow.domain.userlimitusage.service.UserDailyUsageService;
import com.fxflow.domain.wallet.dto.request.ChargeRequest;
import com.fxflow.domain.wallet.dto.request.WithdrawRequest;
import com.fxflow.domain.wallet.dto.response.*;
import com.fxflow.domain.wallet.entity.CurrencyLot;
import com.fxflow.domain.wallet.entity.ExchangeTransaction;
import com.fxflow.domain.wallet.entity.P2pTransfer;
import com.fxflow.domain.wallet.entity.Wallet;
import com.fxflow.domain.wallet.errorcode.ExchangeErrorCode;
import com.fxflow.domain.wallet.errorcode.WalletErrorCode;
import com.fxflow.domain.wallet.policy.WalletPolicy;
import com.fxflow.domain.wallet.repository.CurrencyLotRepository;
import com.fxflow.domain.wallet.repository.ExchangeTransactionRepository;
import com.fxflow.domain.wallet.repository.P2pTransferRepository;
import com.fxflow.domain.wallet.repository.WalletRepository;
import com.fxflow.global.exception.BusinessException;
import com.fxflow.global.fx.ExchangeRateProvider;
import com.fxflow.global.fx.FxRateSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletService {

    private final WalletRepository walletRepository;
    private final FxRateService fxRateService;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final MockBankAccountService mockBankAccountService;
    private final CompanyPoolService companyPoolService;
    private final TransactionLimitValidator transactionLimitValidator;
    private final UserService userService;
    private final UserDailyUsageService userDailyUsageService;
    private final ExchangeTransactionRepository exchangeTransactionRepository;
    private final P2pTransferRepository p2pTransferRepository;
    private final CurrencyLotRepository currencyLotRepository;
    private final ExchangeRateProvider exchangeRateProvider;

    public Wallet getWallet(Long userId, String currencyCode){
        return walletRepository.findByUserIdAndCurrencyCode(userId, currencyCode).orElseThrow(
                () -> new BusinessException(WalletErrorCode.WALLET_NOT_FOUND)
        );
    }

    public Wallet getWalletWithLock(Long userId, String currencyCode) {
        return walletRepository.findByUserIdAndCurrencyCodeWithLock(userId, currencyCode)
                .orElseThrow(() -> new BusinessException(WalletErrorCode.WALLET_NOT_FOUND));
    }

    /** 특정 통화 금액을 현재 환율로 KRW 환산한다. (KRW는 그대로 반환) */
    public BigDecimal toKrwEquivalent(String currencyCode, BigDecimal amount) {
        if ("KRW".equals(currencyCode)) {
            return amount;
        }
        BigDecimal rate = fxRateService.getRate(currencyCode, "KRW");
        return amount.multiply(rate);
    }

    /**
     * 월렛 보유 한도 검증을 위해 KRW+USD 합산 보유액(KRW 환산 기준)을 계산한다.
     * updatedCurrency 지갑은 갱신 후 잔액을, 나머지 통화 지갑은 현재 잔액을 그대로 사용한다.
     */
    public BigDecimal getTotalHoldingKrw(Long userId, String updatedCurrency, BigDecimal updatedBalance) {
        String otherCurrency = "KRW".equals(updatedCurrency) ? "USD" : "KRW";
        BigDecimal otherBalance = getWallet(userId, otherCurrency).getBalance();
        return toKrwEquivalent(updatedCurrency, updatedBalance)
                .add(toKrwEquivalent(otherCurrency, otherBalance));
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

    @Transactional(readOnly = true)
    public TransactionHistoryResponse getTransactionHistory(Long userId, String currency, LedgerEntryType type, LocalDate from, LocalDate to, Pageable pageable) {

        List<Long> walletIds;
        if (currency != null) {
            Wallet wallet = walletRepository.findByUserIdAndCurrencyCode(userId, currency)
                    .orElseThrow(() -> new BusinessException(WalletErrorCode.WALLET_NOT_FOUND));
            walletIds = List.of(wallet.getId());
        } else {
            walletIds = walletRepository.findByUserId(userId).stream().map(Wallet::getId).toList();
        }

        if (walletIds.isEmpty()) {
            return TransactionHistoryResponse.from(Page.empty());
        }

        LocalDateTime fromDateTime = from != null ? from.atStartOfDay() : null;
        LocalDateTime toDateTime = to != null ? to.atTime(23, 59, 59) : null;
        Page<LedgerEntry> entries = ledgerEntryRepository.findByWalletIdInAndFilters(
                walletIds, currency, type, fromDateTime, toDateTime, pageable
        );

        List<String> exchangeRefIds = new ArrayList<>();
        List<String> transferRefIds = new ArrayList<>();

        for (LedgerEntry entry : entries.getContent()) {
            if (entry.getEntryType() == LedgerEntryType.EXCHANGE) {
                exchangeRefIds.add(entry.getRefId());
            } else if (entry.getEntryType() == LedgerEntryType.TRANSFER) {
                transferRefIds.add(entry.getRefId());
            }
        }

        Map<String, ExchangeTransaction> exchangeMap;
        if (!exchangeRefIds.isEmpty()) {
            List<ExchangeTransaction> exchanges = exchangeTransactionRepository.findAllByTransactionIdIn(exchangeRefIds);
            exchangeMap = exchanges.stream()
                    .collect(Collectors.toMap(ExchangeTransaction::getTransactionId, ex -> ex));
        } else {
            exchangeMap = new HashMap<>();
        }

        Map<String, P2pTransfer> transferMap;
        if (!transferRefIds.isEmpty()) {
            List<P2pTransfer> transfers = p2pTransferRepository.findAllWithUsersByIdIn(transferRefIds);
            transferMap = transfers.stream()
                    .collect(Collectors.toMap(P2pTransfer::getTransferId, t -> t));
        } else {
            transferMap = new HashMap<>();
        }

        Page<TransactionResponse> responsePage = entries.map(entry -> switch (entry.getEntryType()) {
            case CHARGE, WITHDRAW ->
                    TransactionResponse.from(entry);

            case EXCHANGE -> {
                ExchangeTransaction exchangeTx = exchangeMap.get(entry.getRefId());
                yield TransactionResponse.from(entry, exchangeTx);
            }

            case TRANSFER -> {
                P2pTransfer transferTx = transferMap.get(entry.getRefId());
                yield TransactionResponse.from(entry, transferTx);
            }
        });

        // 최종 응답 객체 반환
        return TransactionHistoryResponse.from(responsePage);
    }

    // 지갑 충전, mock bank account -> 지갑
    // 지갑 출금, 지갑 -> mock bank account
    // 지갑 <-> 지갑 P2P 송금
    // 지갑 내 환전

    @Transactional
    public TransactionResponse charge(Long userId, ChargeRequest request) {
        BigDecimal amount = request.amount();
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(WalletErrorCode.INVALID_AMOUNT);
        }

        // check wallet balance
        Wallet wallet = getWalletWithLock(userId, "KRW");
        Long walletId = wallet.getId();
        BigDecimal balanceBefore = wallet.getBalance();
        BigDecimal balanceAfter = balanceBefore.add(amount);

        // daily deposit limit check
        User user = userService.getUser(userId);
        transactionLimitValidator.validatePerDeposit(user, amount);
        transactionLimitValidator.validateDailyDeposit(user, amount);
        transactionLimitValidator.validateWalletHolding(user, getTotalHoldingKrw(userId, "KRW", balanceAfter));

        String journalId = LedgerEntry.generateJournalId();

        // mock bank account debit
        mockBankAccountService.withdraw(userId, journalId, amount, "KRW");

        // wallet credit
        wallet.deposit(amount);
        walletRepository.save(wallet);

        companyPoolService.deposit(journalId, "KRW", amount);

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
                        null,
                        null
                );
        ledgerEntryRepository.save(walletEntry);

        userDailyUsageService.addDeposit(userId, LocalDate.now(java.time.ZoneId.of("Asia/Seoul")), amount);
        return TransactionResponse.from(walletEntry);
    }

    @Transactional
    public TransactionResponse withdraw(Long userId, WithdrawRequest request) {

        BigDecimal amount = request.amount();
        if (amount.compareTo(BigDecimal.ZERO) <= 0 || amount.compareTo(WalletPolicy.MAX_KRW_BALANCE) > 0) {
            throw new BusinessException(WalletErrorCode.INVALID_AMOUNT);
        }

        // daily withdrawal limit check
        User user = userService.getUser(userId);
        transactionLimitValidator.validatePerWithdrawal(user, amount);
        transactionLimitValidator.validateDailyWithdrawal(user, amount);

        // check wallet balance
        Wallet wallet = getWalletWithLock(userId, "KRW");
        Long walletId = wallet.getId();
        BigDecimal balanceBefore = wallet.getBalance();
        BigDecimal balanceAfter = balanceBefore.subtract(amount);
        if (balanceAfter.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(WalletErrorCode.INSUFFICIENT_BALANCE);
        }

        String journalId = LedgerEntry.generateJournalId();

        // mock bank account credit
        mockBankAccountService.deposit(userId, journalId, amount, "KRW");

        // wallet credit
        wallet.withdraw(amount);
        walletRepository.save(wallet);

        companyPoolService.withdraw(journalId, "KRW", amount);

        LedgerEntry walletEntry =
                LedgerEntry.create(
                        journalId,
                        LedgerEntryType.WITHDRAW,
                        LedgerDirection.DEBIT,
                        walletId,
                        null,
                        null,
                        "KRW",
                        amount,
                        balanceBefore,
                        balanceAfter,
                        null,
                        null
                );
        ledgerEntryRepository.save(walletEntry);

        userDailyUsageService.addWithdrawal(userId, LocalDate.now(java.time.ZoneId.of("Asia/Seoul")), amount);
        return TransactionResponse.from(walletEntry);
    }

    @Transactional(readOnly = true)
    public WalletProfitResponse getWalletProfit(Long userId, String currencyCode) {
        if ("KRW".equals(currencyCode)) {
            throw new BusinessException(WalletErrorCode.UNSUPPORTED_CURRENCY_FOR_PROFIT);
        }

        Wallet wallet = getWallet(userId, currencyCode);

        // 실현손익
        BigDecimal realizedProfit = currencyLotRepository.sumRealizedProfitByWalletId(wallet.getId());

        // 미실현손익 — 환율은 currencyCode 기준으로 조회
        FxRateSnapshot snapshot = exchangeRateProvider.getLatestRate(currencyCode, "KRW")
                .orElseThrow(() -> new BusinessException(ExchangeErrorCode.FEE_RATE_NOT_FOUND));
        BigDecimal currentRate = snapshot.sellRate();

        List<CurrencyLot> availableLots = currencyLotRepository.findAvailableLotsFIFO(wallet.getId());
        BigDecimal unrealizedProfit = availableLots.stream()
                .map(lot -> currentRate.subtract(lot.getAcquisitionRate())
                        .multiply(lot.getRemainingQuantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return WalletProfitResponse.of(realizedProfit, unrealizedProfit, currentRate);
    }


    // ====== 동시성 비교 테스트용 메서드 (프로덕션 미사용) ======
    // TODO: 테스트 완료 후 제거 예정

    // withdraw 낙관락
    @Retryable(
            includes = ObjectOptimisticLockingFailureException.class,
            maxRetries = 3,
            delay = 100
    )
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TransactionResponse withdrawWithOptimisticLock(Long userId, WithdrawRequest request) {
        // 기존 withdraw()와 완전히 동일 (@Version이 이미 있으니까)
        BigDecimal amount = request.amount();
        if (amount.compareTo(BigDecimal.ZERO) <= 0 || amount.compareTo(WalletPolicy.MAX_KRW_BALANCE) > 0) {
            throw new BusinessException(WalletErrorCode.INVALID_AMOUNT);
        }

        // daily withdrawal limit check
        User user = userService.getUser(userId);
        transactionLimitValidator.validatePerWithdrawal(user, amount);
        transactionLimitValidator.validateDailyWithdrawal(user, amount);

        // check wallet balance
        Wallet wallet = getWallet(userId, "KRW");
        Long walletId = wallet.getId();
        BigDecimal balanceBefore = wallet.getBalance();
        BigDecimal balanceAfter = balanceBefore.subtract(amount);
        if (balanceAfter.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(WalletErrorCode.INSUFFICIENT_BALANCE);
        }

        String journalId = LedgerEntry.generateJournalId();

        // mock bank account credit
        mockBankAccountService.deposit(userId, journalId, amount, "KRW");

        // wallet credit
        wallet.withdraw(amount);
        walletRepository.save(wallet);

        companyPoolService.withdraw(journalId, "KRW", amount);

        LedgerEntry walletEntry =
                LedgerEntry.create(
                        journalId,
                        LedgerEntryType.WITHDRAW,
                        LedgerDirection.DEBIT,
                        walletId,
                        null,
                        null,
                        "KRW",
                        amount,
                        balanceBefore,
                        balanceAfter,
                        null,
                        null
                );
        ledgerEntryRepository.save(walletEntry);

        userDailyUsageService.addWithdrawal(userId, LocalDate.now(java.time.ZoneId.of("Asia/Seoul")), amount);
        return TransactionResponse.from(walletEntry);
    }

    // withdraw 비관락
    @Transactional
    public TransactionResponse withdrawWithPessimisticLock(Long userId, WithdrawRequest request) {
        // getWallet → getWalletWithLock 으로만 교체, 나머지 동일
        BigDecimal amount = request.amount();
        if (amount.compareTo(BigDecimal.ZERO) <= 0 || amount.compareTo(WalletPolicy.MAX_KRW_BALANCE) > 0) {
            throw new BusinessException(WalletErrorCode.INVALID_AMOUNT);
        }

        // daily withdrawal limit check
        User user = userService.getUser(userId);
        transactionLimitValidator.validatePerWithdrawal(user, amount);
        transactionLimitValidator.validateDailyWithdrawal(user, amount);

        // check wallet balance
        Wallet wallet = walletRepository.findByUserIdAndCurrencyCodeWithLock(userId, "KRW")
                .orElseThrow(() -> new BusinessException(WalletErrorCode.WALLET_NOT_FOUND));

        Long walletId = wallet.getId();
        BigDecimal balanceBefore = wallet.getBalance();
        BigDecimal balanceAfter = balanceBefore.subtract(amount);
        if (balanceAfter.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(WalletErrorCode.INSUFFICIENT_BALANCE);
        }

        String journalId = LedgerEntry.generateJournalId();

        // mock bank account credit
        mockBankAccountService.deposit(userId, journalId, amount, "KRW");

        // wallet credit
        wallet.withdraw(amount);
        walletRepository.save(wallet);

        companyPoolService.withdraw(journalId, "KRW", amount);

        LedgerEntry walletEntry =
                LedgerEntry.create(
                        journalId,
                        LedgerEntryType.WITHDRAW,
                        LedgerDirection.DEBIT,
                        walletId,
                        null,
                        null,
                        "KRW",
                        amount,
                        balanceBefore,
                        balanceAfter,
                        null,
                        null
                );
        ledgerEntryRepository.save(walletEntry);

        userDailyUsageService.addWithdrawal(userId, LocalDate.now(java.time.ZoneId.of("Asia/Seoul")), amount);
        return TransactionResponse.from(walletEntry);
    }

    // withdraw 비관락 delay O (test용)
    @Transactional
    public TransactionResponse withdrawWithPessimisticLockWithDelay(Long userId, WithdrawRequest request) {
        // getWallet → getWalletWithLock 으로만 교체, 나머지 동일
        BigDecimal amount = request.amount();
        if (amount.compareTo(BigDecimal.ZERO) <= 0 || amount.compareTo(WalletPolicy.MAX_KRW_BALANCE) > 0) {
            throw new BusinessException(WalletErrorCode.INVALID_AMOUNT);
        }

        // daily withdrawal limit check
        User user = userService.getUser(userId);
        transactionLimitValidator.validatePerWithdrawal(user, amount);
        transactionLimitValidator.validateDailyWithdrawal(user, amount);

        // check wallet balance
        log.info("before wallet lock");
        Wallet wallet = walletRepository.findByUserIdAndCurrencyCodeWithLock(userId, "KRW")
                .orElseThrow(() -> new BusinessException(WalletErrorCode.WALLET_NOT_FOUND));
        log.info("after wallet lock");

        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        Long walletId = wallet.getId();
        BigDecimal balanceBefore = wallet.getBalance();
        BigDecimal balanceAfter = balanceBefore.subtract(amount);
        if (balanceAfter.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(WalletErrorCode.INSUFFICIENT_BALANCE);
        }

        String journalId = LedgerEntry.generateJournalId();

        // mock bank account credit
        mockBankAccountService.deposit(userId, journalId, amount, "KRW");

        // wallet credit
        wallet.withdraw(amount);
        walletRepository.save(wallet);

        companyPoolService.withdraw(journalId, "KRW", amount);

        LedgerEntry walletEntry =
                LedgerEntry.create(
                        journalId,
                        LedgerEntryType.WITHDRAW,
                        LedgerDirection.DEBIT,
                        walletId,
                        null,
                        null,
                        "KRW",
                        amount,
                        balanceBefore,
                        balanceAfter,
                        null,
                        null
                );
        ledgerEntryRepository.save(walletEntry);

        userDailyUsageService.addWithdrawal(userId, LocalDate.now(java.time.ZoneId.of("Asia/Seoul")), amount);
        return TransactionResponse.from(walletEntry);
    }

    // withdraw 원자적
    @Transactional
    public TransactionResponse withdrawWithAtomicQuery(Long userId, WithdrawRequest request) {
        BigDecimal amount = request.amount();

        User user = userService.getUser(userId);
        transactionLimitValidator.validatePerWithdrawal(user, amount);
        transactionLimitValidator.validateDailyWithdrawal(user, amount);

        int updated = walletRepository.withdrawAtomic(userId, amount);
        if (updated == 0) throw new BusinessException(WalletErrorCode.INSUFFICIENT_BALANCE);

        Wallet wallet = getWallet(userId, "KRW"); // 차감 후 잔액
        Long walletId = wallet.getId();
        BigDecimal balanceAfter = wallet.getBalance();           // 이미 차감된 값
        BigDecimal balanceBefore = balanceAfter.add(amount);    // 역산
        if (balanceAfter.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(WalletErrorCode.INSUFFICIENT_BALANCE);
        }

        String journalId = LedgerEntry.generateJournalId();

        // mock bank account credit
        mockBankAccountService.deposit(userId, journalId, amount, "KRW");


        companyPoolService.withdraw(journalId, "KRW", amount);

        LedgerEntry walletEntry =
                LedgerEntry.create(
                        journalId,
                        LedgerEntryType.WITHDRAW,
                        LedgerDirection.DEBIT,
                        walletId,
                        null,
                        null,
                        "KRW",
                        amount,
                        balanceBefore,
                        balanceAfter,
                        null,
                        null
                );
        ledgerEntryRepository.save(walletEntry);

        userDailyUsageService.addWithdrawal(userId, LocalDate.now(java.time.ZoneId.of("Asia/Seoul")), amount);
        return TransactionResponse.from(walletEntry);
    }
}