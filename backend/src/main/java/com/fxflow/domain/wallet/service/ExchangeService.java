package com.fxflow.domain.wallet.service;

import com.fxflow.domain.companypool.service.CompanyPoolService;
import com.fxflow.domain.ledger.entity.LedgerEntry;
import com.fxflow.domain.ledger.enums.LedgerDirection;
import com.fxflow.domain.ledger.enums.LedgerEntryType;
import com.fxflow.domain.ledger.enums.LedgerRefType;
import com.fxflow.domain.ledger.repository.LedgerEntryRepository;
import com.fxflow.domain.transactionlimit.validator.TransactionLimitValidator;
import com.fxflow.domain.user.entity.User;
import com.fxflow.domain.user.service.UserService;
import com.fxflow.domain.wallet.config.ExchangeFeeProperties;
import com.fxflow.domain.wallet.config.ExchangeProperties;
import com.fxflow.domain.wallet.dto.cache.ExchangeQuoteCache;
import com.fxflow.domain.wallet.dto.request.ExchangeQuoteRequest;
import com.fxflow.domain.wallet.dto.request.ExchangeRequest;
import com.fxflow.domain.wallet.dto.response.ExchangeQuoteResponse;
import com.fxflow.domain.wallet.dto.response.ExchangeResponse;
import com.fxflow.domain.wallet.entity.ExchangeTransaction;
import com.fxflow.domain.wallet.entity.Wallet;
import com.fxflow.domain.wallet.errorcode.ExchangeErrorCode;
import com.fxflow.domain.wallet.errorcode.WalletErrorCode;
import com.fxflow.domain.wallet.repository.ExchangeTransactionRepository;
import com.fxflow.domain.wallet.repository.WalletRepository;
import com.fxflow.global.exception.BusinessException;
import com.fxflow.global.fx.ExchangeRateProvider;
import com.fxflow.global.fx.FxRateSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExchangeService {

    private static final BigDecimal MIN_KRW_AMOUNT = new BigDecimal("1000"); // 1,000원
    private static final BigDecimal MIN_USD_AMOUNT = new BigDecimal("1");

    private final ExchangeRateProvider exchangeRateProvider;
    private final ExchangeFeeProperties exchangeFeeProperties;
    private final ExchangeProperties exchangeProperties;
    private final RedisTemplate<String, Object> redisTemplate;
    private final TransactionLimitValidator transactionLimitValidator;
    private final UserService userService;
    private final WalletService walletService;
    private final WalletRepository walletRepository;
    private final ExchangeTransactionRepository exchangeTransactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final CompanyPoolService companyPoolService;
    private final CurrencyLotService currencyLotService;

    public ExchangeQuoteResponse getExchangeQuote(Long userId, ExchangeQuoteRequest request) {

        /*
         * base=USD, quote=KRW로 고정
         * from == "USD"(USD→KRW, base 매도) → sellRate,
         * from == "KRW"(KRW→USD, base 매수) → buyRate,
         * */

        String fromCurrency = request.fromCurrency();
        String toCurrency = request.toCurrency();
        BigDecimal amount = request.amount();

        // 환전 최소값 검증
        if (fromCurrency.equals("KRW") && amount.compareTo(MIN_KRW_AMOUNT) < 0) {
            throw new BusinessException(ExchangeErrorCode.MINIMUM_AMOUNT_NOT_MET);
        } else if (fromCurrency.equals("USD") && amount.compareTo(MIN_USD_AMOUNT) < 0) {
            throw new BusinessException(ExchangeErrorCode.MINIMUM_AMOUNT_NOT_MET);
        }

        FxRateSnapshot fxRateSnapshot = exchangeRateProvider.getLatestRate("USD", "KRW")
                .orElseThrow( () -> new BusinessException(ExchangeErrorCode.FEE_RATE_NOT_FOUND));
        BigDecimal appliedRate = fromCurrency.equals("USD") ? fxRateSnapshot.sellRate() : fxRateSnapshot.buyRate(); // todo: 현재 USD <-> WON 고정, 추후 다른 환율 추가 시 수정 필요

        BigDecimal feeRate = exchangeFeeProperties.getRates().get(fromCurrency + "-" + toCurrency);
        if (feeRate == null) {
            throw new BusinessException(ExchangeErrorCode.FEE_RATE_NOT_FOUND);
        }

        BigDecimal toAmount = fromCurrency.equals("KRW")
                ? amount.divide(appliedRate, 2, RoundingMode.FLOOR) // 소수점 버림
                : amount.multiply(appliedRate).setScale(0, RoundingMode.FLOOR); // 소수점 버림
        BigDecimal feeAmount = amount.multiply(feeRate);  // 출발 통화 기준 수수료
        BigDecimal totalAmount = amount.add(feeAmount);  // 출발 통화 기준 총 차감액
        LocalDateTime expiredAt = LocalDateTime.now().plusMinutes(exchangeProperties.getQuoteExpirationMinutes());
        String quoteId = UUID.randomUUID().toString();

        // Quote redis에 저장
        ExchangeQuoteCache cache = new ExchangeQuoteCache(
                userId, fromCurrency, toCurrency, amount, toAmount,
                fxRateSnapshot.midRate(), fxRateSnapshot.spread(), appliedRate,
                feeAmount, totalAmount
        );
        redisTemplate.opsForValue().set(
                "quote:" + quoteId,
                cache,
                Duration.ofMinutes(exchangeProperties.getQuoteExpirationMinutes())
        );

        return ExchangeQuoteResponse.from(
                fromCurrency,
                toCurrency,
                amount,
                toAmount,
                appliedRate,
                feeAmount,
                totalAmount,
                expiredAt,
                quoteId
        );
    }

    @Transactional
    public ExchangeResponse exchange(Long userId, ExchangeRequest request) {
        String quoteId = request.quoteId();

        // check quote (redis)
        ExchangeQuoteCache cache = (ExchangeQuoteCache) redisTemplate.opsForValue().get("quote:" + quoteId);
        if (cache == null) {throw new BusinessException(ExchangeErrorCode.QUOTE_NOT_FOUND);}
        if (!cache.userId().equals(userId)) {
            throw new BusinessException(ExchangeErrorCode.QUOTE_NOT_FOUND); // 본인 견적 아님
        }

        // check balance in user wallet
        String fromCurrency = cache.fromCurrency();
        String toCurrency = cache.toCurrency();
        Wallet fromWallet = walletService.getWalletWithLock(userId, cache.fromCurrency());
        Wallet toWallet = walletService.getWalletWithLock(userId, cache.toCurrency());

        if (fromWallet.getBalance().compareTo(cache.totalAmount()) < 0){
            throw new BusinessException(WalletErrorCode.INSUFFICIENT_BALANCE);
        }

        // -- 제한 검증 --
        // check user's one/daily/yearly exchange limit
        User user = userService.getUser(userId);
        transactionLimitValidator.validateExchange(user, cache.fromAmount());  // 환전하려는 금액 검증

        // check wallet holding
        BigDecimal toBalanceAfter = toWallet.getBalance().add(cache.toAmount()); // deposit 전에 미리 계산
        transactionLimitValidator.validateWalletHolding(user, toBalanceAfter);

        // -- exchange --
        BigDecimal fromBalanceBefore = fromWallet.getBalance();
        BigDecimal toBalanceBefore = toWallet.getBalance();

        // wallet 값 정산
        fromWallet.withdraw(cache.totalAmount());  // 수수료 값이랑 환전하려는 돈
        toWallet.deposit(cache.toAmount());
        walletRepository.save(fromWallet);
        walletRepository.save(toWallet);

        // lot 정산
        String transferId = ExchangeTransaction.generateTransferId();
        currencyLotService.settleLots(fromWallet, toWallet, cache.fromAmount(), cache.finalRate(), transferId);

        // Exchange transaction 저장
        String journalId = LedgerEntry.generateJournalId();
        String idempotencyKey = UUID.randomUUID().toString();

        ExchangeTransaction exchangeTransaction = ExchangeTransaction.create(
                transferId,
                user,
                fromWallet,
                toWallet,
                fromCurrency,
                toCurrency,
                cache.fromAmount(),
                cache.toAmount(),
                cache.baseRate(),
                cache.spreadRate(),
                cache.finalRate(),
                idempotencyKey,
                cache.feeAmount());
        exchangeTransactionRepository.save(exchangeTransaction);

        // ledger entry 저장
        LedgerEntry fromEntry = LedgerEntry.create(
                journalId, LedgerEntryType.EXCHANGE, LedgerDirection.DEBIT,
                fromWallet.getId(), null, null,
                cache.fromCurrency(), cache.totalAmount(),
                fromBalanceBefore, fromWallet.getBalance(),
                LedgerRefType.EXCHANGE, exchangeTransaction.getTransactionId()
        );
        LedgerEntry toEntry = LedgerEntry.create(
                journalId, LedgerEntryType.EXCHANGE, LedgerDirection.CREDIT,
                toWallet.getId(), null, null,
                cache.toCurrency(), cache.toAmount(),
                toBalanceBefore, toWallet.getBalance(),
                LedgerRefType.EXCHANGE, exchangeTransaction.getTransactionId()
        );
        ledgerEntryRepository.save(fromEntry);
        ledgerEntryRepository.save(toEntry);

        // pool 갱신 X, 출금할 때 실제 자금 이동

        // transaction 완료 후 quote 삭제
        redisTemplate.delete("quote:" + quoteId);

        return ExchangeResponse.from(exchangeTransaction);
    }
}
