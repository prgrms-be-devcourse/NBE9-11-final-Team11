package com.fxflow.domain.wallet.service;

import com.fxflow.domain.ledger.repository.LedgerEntryRepository;
import com.fxflow.domain.transactionlimit.validator.TransactionLimitValidator;
import com.fxflow.domain.user.entity.User;
import com.fxflow.domain.user.service.UserService;
import com.fxflow.domain.wallet.dto.cache.ExchangeQuoteCache;
import com.fxflow.domain.wallet.dto.request.ExchangeRequest;
import com.fxflow.domain.wallet.entity.ExchangeTransaction;
import com.fxflow.domain.wallet.entity.Wallet;
import com.fxflow.domain.wallet.repository.ExchangeTransactionRepository;
import com.fxflow.domain.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ExchangeServiceFeeTest {

    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOperations;
    @Mock private WalletService walletService;
    @Mock private WalletRepository walletRepository;
    @Mock private UserService userService;
    @Mock
    private TransactionLimitValidator transactionLimitValidator;
    @Mock private ExchangeTransactionRepository exchangeTransactionRepository;
    @Mock private LedgerEntryRepository ledgerEntryRepository;

    @InjectMocks
    private ExchangeService exchangeService;

    private Long userId;
    private User user;
    private Wallet fromWallet;
    private Wallet toWallet;
    private ExchangeQuoteCache cache;
    private String quoteId;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        userId = 1L;
        user = User.create("email", "password", "name");
        ReflectionTestUtils.setField(user, "id", userId);

        fromWallet = Wallet.create(user, "KRW", new BigDecimal("1000000"));
        ReflectionTestUtils.setField(fromWallet, "id", 10L);

        toWallet = Wallet.create(user, "USD", new BigDecimal("0"));
        ReflectionTestUtils.setField(toWallet, "id", 11L);

        quoteId = "123L";

        // toAmount=366.7, feeAmount=1.83(0.5%), totalAmount=368.53 가정
        cache = new ExchangeQuoteCache(
                userId, "KRW", "USD",
                new BigDecimal("500000"),
                new BigDecimal("366.70"),
                new BigDecimal("1350"), new BigDecimal("0.01"), new BigDecimal("1363.5"),
                new BigDecimal("1.83"),
                new BigDecimal("368.53")
        );

        given(walletService.getWallet(userId, "KRW")).willReturn(fromWallet);
        given(walletService.getWallet(userId, "USD")).willReturn(toWallet);
        given(userService.getUser(userId)).willReturn(user);
        given(valueOperations.get("quote:" + quoteId)).willReturn(cache);
        given(exchangeTransactionRepository.save(any(ExchangeTransaction.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("체결 시 toWallet에는 수수료가 차감된 금액만 적립되어야 한다")
    void exchange_deductsFeeFromDepositedAmount() {
        // given
        ExchangeRequest request = new ExchangeRequest(quoteId);
        BigDecimal expectedNetAmount = cache.toAmount().subtract(cache.feeAmount()); // 366.70 - 1.83 = 364.87

        // when
        exchangeService.exchange(userId, request);

        // then
        assertThat(toWallet.getBalance())
                .isEqualByComparingTo(expectedNetAmount);
    }

    @Test
    @DisplayName("체결된 ExchangeTransaction에는 견적 시점의 수수료가 그대로 저장되어야 한다")
    void exchange_storesFeeAmountOnTransaction() {
        // given
        ExchangeRequest request = new ExchangeRequest(quoteId);
        ArgumentCaptor<ExchangeTransaction> captor = ArgumentCaptor.forClass(ExchangeTransaction.class);

        // when
        exchangeService.exchange(userId, request);

        // then
        verify(exchangeTransactionRepository).save(captor.capture());
        assertThat(captor.getValue().getFeeAmount()).isEqualByComparingTo(cache.feeAmount());
    }
}