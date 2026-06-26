package com.fxflow.domain.wallet.service;

import com.fxflow.domain.ledger.entity.LedgerEntry;
import com.fxflow.domain.ledger.repository.LedgerEntryRepository;
import com.fxflow.domain.transactionlimit.errorcode.TransactionLimitErrorCode;
import com.fxflow.domain.transactionlimit.validator.TransactionLimitValidator;
import com.fxflow.domain.user.entity.User;
import com.fxflow.domain.user.service.UserService;
import com.fxflow.domain.userlimitusage.service.UserExchangeUsageService;
import com.fxflow.domain.wallet.dto.cache.ExchangeQuoteCache;
import com.fxflow.domain.wallet.dto.request.ExchangeRequest;
import com.fxflow.domain.wallet.dto.response.ExchangeResponse;
import com.fxflow.domain.wallet.entity.ExchangeTransaction;
import com.fxflow.domain.wallet.entity.Wallet;
import com.fxflow.domain.wallet.errorcode.ExchangeErrorCode;
import com.fxflow.domain.wallet.errorcode.WalletErrorCode;
import com.fxflow.domain.wallet.repository.ExchangeTransactionRepository;
import com.fxflow.domain.wallet.repository.WalletRepository;
import com.fxflow.global.exception.BusinessException;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExchangeServiceExchangeTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOperations;
    @Mock private WalletService walletService;
    @Mock private WalletRepository walletRepository;
    @Mock private UserService userService;
    @Mock private TransactionLimitValidator transactionLimitValidator;
    @Mock private ExchangeTransactionRepository exchangeTransactionRepository;
    @Mock private LedgerEntryRepository ledgerEntryRepository;
    @Mock private CurrencyLotService currencyLotService;
    @Mock private UserExchangeUsageService userExchangeUsageService;

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
        cache = new ExchangeQuoteCache(
                userId, "KRW", "USD",
                new BigDecimal("500000"), new BigDecimal("370.12"),
                new BigDecimal("1350"), new BigDecimal("0.01"), new BigDecimal("1363.5"),
                new BigDecimal("2500"), new BigDecimal("502500")
        );
    }

    @Test
    @DisplayName("환전 체결 성공")
    void exchange_success() {
        // given
        ExchangeRequest request = new ExchangeRequest(quoteId);
        given(valueOperations.get("quote:" + quoteId)).willReturn(cache);
        given(walletService.getWalletWithLock(userId, "KRW")).willReturn(fromWallet);
        given(walletService.getWalletWithLock(userId, "USD")).willReturn(toWallet);
        given(userService.getUser(userId)).willReturn(user);
        given(exchangeTransactionRepository.save(any(ExchangeTransaction.class)))
                .willAnswer(invocation -> {
                    ExchangeTransaction tx = invocation.getArgument(0);
                    ReflectionTestUtils.setField(tx, "transactionId", "EX_27a10642b4a34fa4a4207cf8b9a65963");
                    return tx;
                });

        // when
        ExchangeResponse response = exchangeService.exchange(userId, request);

        // then
        assertThat(response).isNotNull();
        assertThat(response.transactionId()).isEqualTo("EX_27a10642b4a34fa4a4207cf8b9a65963");
        assertThat(fromWallet.getBalance()).isEqualByComparingTo(new BigDecimal("497500"));
        assertThat(toWallet.getBalance()).isEqualByComparingTo(new BigDecimal("370.12"));

        verify(walletRepository).save(fromWallet);
        verify(walletRepository).save(toWallet);
        verify(ledgerEntryRepository, times(2)).save(any(LedgerEntry.class));
        verify(redisTemplate).delete("quote:" + quoteId);

        // KRW -> USD 방향은 한도 검증/사용량 누적 대상이다
        verify(transactionLimitValidator).validateExchange(eq(user), eq(cache.fromAmount()));
        verify(userExchangeUsageService).addDailyExchange(eq(userId), any(), eq(cache.fromAmount()));
        verify(userExchangeUsageService).addAnnualExchange(eq(userId), any(), eq(cache.fromAmount()));
    }

    @Test
    @DisplayName("USD -> KRW 환전은 한도 검증 및 사용량 누적을 하지 않는다")
    void exchange_usdToKrw_skipsLimitValidationAndUsage() {
        // given
        ExchangeQuoteCache usdToKrwCache = new ExchangeQuoteCache(
                userId, "USD", "KRW",
                new BigDecimal("370.12"), new BigDecimal("500000"),
                new BigDecimal("1350"), new BigDecimal("0.01"), new BigDecimal("1336.5"),
                new BigDecimal("2500"), new BigDecimal("372.62")
        );
        Wallet usdFromWallet = Wallet.create(user, "USD", new BigDecimal("1000"));
        ReflectionTestUtils.setField(usdFromWallet, "id", 12L);
        Wallet krwToWallet = Wallet.create(user, "KRW", new BigDecimal("0"));
        ReflectionTestUtils.setField(krwToWallet, "id", 13L);

        ExchangeRequest request = new ExchangeRequest(quoteId);
        given(valueOperations.get("quote:" + quoteId)).willReturn(usdToKrwCache);
        given(walletService.getWalletWithLock(userId, "USD")).willReturn(usdFromWallet);
        given(walletService.getWalletWithLock(userId, "KRW")).willReturn(krwToWallet);
        given(userService.getUser(userId)).willReturn(user);
        given(exchangeTransactionRepository.save(any(ExchangeTransaction.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        ExchangeResponse response = exchangeService.exchange(userId, request);

        // then
        assertThat(response).isNotNull();
        verify(transactionLimitValidator, never()).validateExchange(any(), any());
        verify(userExchangeUsageService, never()).addDailyExchange(any(), any(), any());
        verify(userExchangeUsageService, never()).addAnnualExchange(any(), any(), any());

        // 보유 한도 검증은 방향과 무관하게 그대로 수행된다
        verify(transactionLimitValidator).validateWalletHolding(eq(user), any(BigDecimal.class));
        verify(currencyLotService).settleLots(eq(fromWallet), eq(toWallet), eq(cache.fromAmount()), eq(cache.toAmount()), eq(cache.finalRate()), anyString());
    }

    @Test
    @DisplayName("견적이 존재하지 않으면 QUOTE_NOT_FOUND 예외 발생")
    void exchange_quoteNotFound() {
        // given
        ExchangeRequest request = new ExchangeRequest(quoteId);
        given(valueOperations.get("quote:" + quoteId)).willReturn(null);

        // when & then
        assertThatThrownBy(() -> exchangeService.exchange(userId, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ExchangeErrorCode.QUOTE_NOT_FOUND);

        verify(walletRepository, never()).save(any());
        verify(ledgerEntryRepository, never()).save(any());
    }

    @Test
    @DisplayName("본인의 견적이 아니면 QUOTE_NOT_FOUND 예외 발생")
    void exchange_quoteNotOwnedByUser() {
        // given
        Long otherUserId = 2L;
        ExchangeRequest request = new ExchangeRequest(quoteId);
        given(valueOperations.get("quote:" + quoteId)).willReturn(cache); // cache.userId() == 1L

        // when & then
        assertThatThrownBy(() -> exchangeService.exchange(otherUserId, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ExchangeErrorCode.QUOTE_NOT_FOUND);
    }

    @Test
    @DisplayName("잔액 부족 시 INSUFFICIENT_BALANCE 예외 발생")
    void exchange_insufficientBalance() {
        // given
        ExchangeRequest request = new ExchangeRequest(quoteId);
        Wallet poorWallet = Wallet.create(user, "KRW", new BigDecimal("100")); // 부족한 잔액
        ReflectionTestUtils.setField(poorWallet, "id", 10L);

        given(valueOperations.get("quote:" + quoteId)).willReturn(cache);
        given(walletService.getWalletWithLock(userId, "KRW")).willReturn(poorWallet);
        given(walletService.getWalletWithLock(userId, "USD")).willReturn(toWallet);

        // when & then
        assertThatThrownBy(() -> exchangeService.exchange(userId, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(WalletErrorCode.INSUFFICIENT_BALANCE);

        verify(walletRepository, never()).save(any());
        verify(exchangeTransactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("환전 한도 초과 시 예외가 발생하고 잔액 변경이 일어나지 않는다")
    void exchange_limitExceeded() {
        // given
        ExchangeRequest request = new ExchangeRequest(quoteId);
        given(valueOperations.get("quote:" + quoteId)).willReturn(cache);
        given(walletService.getWalletWithLock(userId, "KRW")).willReturn(fromWallet);
        given(walletService.getWalletWithLock(userId, "USD")).willReturn(toWallet);
        given(userService.getUser(userId)).willReturn(user);

        willThrow(new BusinessException(TransactionLimitErrorCode.DAILY_EXCHANGE_LIMIT_EXCEEDED))
                .given(transactionLimitValidator)
                .validateExchange(eq(user), any(BigDecimal.class));

        // when & then
        assertThatThrownBy(() -> exchangeService.exchange(userId, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(TransactionLimitErrorCode.DAILY_EXCHANGE_LIMIT_EXCEEDED);

        verify(walletRepository, never()).save(any());
        verify(ledgerEntryRepository, never()).save(any());
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    @DisplayName("체결 성공 시 journalId가 두 ledger entry에 동일하게 적용된다")
    void exchange_sameJournalIdForBothEntries() {
        // given
        ExchangeRequest request = new ExchangeRequest(quoteId);
        given(valueOperations.get("quote:" + quoteId)).willReturn(cache);
        given(walletService.getWalletWithLock(userId, "KRW")).willReturn(fromWallet);
        given(walletService.getWalletWithLock(userId, "USD")).willReturn(toWallet);
        given(userService.getUser(userId)).willReturn(user);
        given(exchangeTransactionRepository.save(any(ExchangeTransaction.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);

        // when
        exchangeService.exchange(userId, request);

        // then
        verify(ledgerEntryRepository, times(2)).save(captor.capture());
        List<LedgerEntry> entries = captor.getAllValues();
        assertThat(entries.get(0).getJournalId()).isEqualTo(entries.get(1).getJournalId());
    }
}
