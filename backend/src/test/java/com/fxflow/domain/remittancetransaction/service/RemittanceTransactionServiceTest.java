package com.fxflow.domain.remittancetransaction.service;

import com.fxflow.domain.companypool.service.CompanyPoolService;
import com.fxflow.domain.mockbankaccount.service.MockBankAccountService;
import com.fxflow.domain.remittancetransaction.dto.cache.RemittanceQuoteCache;
import com.fxflow.domain.remittancetransaction.dto.request.RemittanceTransactionCreateRequest;
import com.fxflow.domain.remittancetransaction.dto.request.RemittanceTransactionQuoteRequest;
import com.fxflow.domain.remittancetransaction.dto.response.*;
import com.fxflow.domain.remittancetransaction.entity.Recipient;
import com.fxflow.domain.remittancetransaction.entity.RemittanceTransaction;
import com.fxflow.domain.remittancetransaction.entity.VirtualAccount;
import com.fxflow.domain.remittancetransaction.enums.RemittanceMethod;
import com.fxflow.domain.remittancetransaction.enums.RemittanceReason;
import com.fxflow.domain.remittancetransaction.enums.TransferStatus;
import com.fxflow.domain.remittancetransaction.enums.VirtualAccountStatus;
import com.fxflow.domain.remittancetransaction.errorcode.RecipientErrorCode;
import com.fxflow.domain.remittancetransaction.errorcode.RemittanceTransactionErrorCode;
import com.fxflow.domain.remittancetransaction.event.RemittanceFundedEvent;
import com.fxflow.domain.remittancetransaction.repository.RecipientRepository;
import com.fxflow.domain.remittancetransaction.repository.RemittanceTransactionRepository;
import com.fxflow.domain.remittancetransaction.repository.VirtualAccountRepository;
import com.fxflow.domain.remittancetransaction.validator.RemittanceValidator;
import com.fxflow.domain.transactionlimit.entity.TransactionLimit;
import com.fxflow.domain.transactionlimit.enums.LimitTier;
import com.fxflow.domain.transactionlimit.enums.LimitType;
import com.fxflow.domain.transactionlimit.errorcode.TransactionLimitErrorCode;
import com.fxflow.domain.transactionlimit.repository.TransactionLimitRepository;
import com.fxflow.domain.user.entity.User;
import com.fxflow.domain.user.repository.UserRepository;
import com.fxflow.domain.userlimitusage.entity.UserAnnualUsage;
import com.fxflow.domain.userlimitusage.repository.UserAnnualUsageRepository;
import com.fxflow.global.exception.BusinessException;
import com.fxflow.global.fx.ExchangeRateProvider;
import com.fxflow.global.fx.FxRateSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RemittanceTransactionServiceTest {

    @Mock
    private UserAnnualUsageRepository userAnnualUsageRepository;

    @Mock
    private TransactionLimitRepository transactionLimitRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RecipientRepository recipientRepository;

    @Mock
    private RemittanceTransactionRepository remittanceTransactionRepository;

    @Mock
    private VirtualAccountRepository virtualAccountRepository;

    @Mock
    private RemittanceQuoteProvider remittanceQuoteProvider;

    @Mock
    private RemittanceValidator remittanceValidator;

    @Mock
    private MockBankAccountService mockBankAccountService;

    @Mock
    private CompanyPoolService companyPoolService;

    @Mock
    private RecipientPayoutAccountService recipientPayoutAccountService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private ExchangeRateProvider exchangeRateProvider;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private RemittanceTransactionService remittanceTransactionService;

    @Test
    @DisplayName("성공: 해외송금 견적을 산출하고 Redis에 저장한다")
    void createQuote_success() {
        // given
        Long userId = 1L;
        RemittanceTransactionQuoteRequest request = createQuoteRequest();
        Recipient recipient = createRecipient(userId);
        FxRateSnapshot fxRateSnapshot = createFxRateSnapshot();

        when(recipientRepository.findByIdAndUserIdAndDeletedAtIsNull(request.recipientId(), userId))
                .thenReturn(Optional.of(recipient));
        when(exchangeRateProvider.getLatestRate("USD", "KRW"))
                .thenReturn(Optional.of(fxRateSnapshot));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // when
        RemittanceTransactionQuoteResponse response =
                remittanceTransactionService.createQuote(userId, request);

        // then
        assertThat(response.sendAmountKrw()).isEqualByComparingTo(new BigDecimal("1000000"));
        assertThat(response.receiveAmountUsd()).isEqualByComparingTo(new BigDecimal("740.00"));
        assertThat(response.exchangeRate()).isEqualByComparingTo(new BigDecimal("1351.350"));
        assertThat(response.fixedFee()).isEqualByComparingTo(new BigDecimal("3000.00"));
        assertThat(response.percentFee()).isEqualByComparingTo(new BigDecimal("5000"));
        assertThat(response.totalFee()).isEqualByComparingTo(new BigDecimal("8000.00"));
        assertThat(response.quoteId()).isNotBlank();
        assertThat(response.expiredAt()).isNotNull();

        verify(remittanceValidator).validateLimits(userId, new BigDecimal("740.00074000"));

        ArgumentCaptor<RemittanceQuoteCache> cacheCaptor =
                ArgumentCaptor.forClass(RemittanceQuoteCache.class);
        verify(valueOperations).set(
                startsWith("remittance:quote:"),
                cacheCaptor.capture(),
                eq(Duration.ofMinutes(5))
        );

        RemittanceQuoteCache cache = cacheCaptor.getValue();
        assertThat(cache.userId()).isEqualTo(userId);
        assertThat(cache.recipientId()).isEqualTo(request.recipientId());
        assertThat(cache.sendCurrency()).isEqualTo("KRW");
        assertThat(cache.sendAmount()).isEqualByComparingTo(new BigDecimal("1000000.00000000"));
        assertThat(cache.receiveCurrency()).isEqualTo("USD");
        assertThat(cache.receiveAmount()).isEqualByComparingTo(new BigDecimal("740.00074000"));
        assertThat(cache.appliedRate()).isEqualByComparingTo(new BigDecimal("1351.350"));
        assertThat(cache.feeAmount()).isEqualByComparingTo(new BigDecimal("8000.00000000"));
        assertThat(cache.amountKrw()).isEqualByComparingTo(new BigDecimal("1000000.00000000"));
        assertThat(cache.amountUsd()).isEqualByComparingTo(new BigDecimal("740.00074000"));
    }

    @Test
    @DisplayName("실패: 해외송금 견적 산출 시 수취인을 찾을 수 없으면 예외가 발생한다")
    void createQuote_fail_recipientNotFound() {
        // given
        Long userId = 1L;
        RemittanceTransactionQuoteRequest request = createQuoteRequest();

        when(recipientRepository.findByIdAndUserIdAndDeletedAtIsNull(request.recipientId(), userId))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> remittanceTransactionService.createQuote(userId, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(RecipientErrorCode.RECIPIENT_NOT_FOUND);

        verifyNoInteractions(exchangeRateProvider);
        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("실패: 해외송금 견적 산출 시 환율 정보가 없으면 예외가 발생한다")
    void createQuote_fail_exchangeRateNotFound() {
        // given
        Long userId = 1L;
        RemittanceTransactionQuoteRequest request = createQuoteRequest();
        Recipient recipient = createRecipient(userId);

        when(recipientRepository.findByIdAndUserIdAndDeletedAtIsNull(request.recipientId(), userId))
                .thenReturn(Optional.of(recipient));
        when(exchangeRateProvider.getLatestRate("USD", "KRW"))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> remittanceTransactionService.createQuote(userId, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(RemittanceTransactionErrorCode.REMITTANCE_EXCHANGE_RATE_NOT_FOUND);

        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("실패: 해외송금 견적 산출 시 한도를 초과하면 Redis에 저장하지 않는다")
    void createQuote_fail_limitExceeded() {
        // given
        Long userId = 1L;
        RemittanceTransactionQuoteRequest request = createQuoteRequest();
        Recipient recipient = createRecipient(userId);
        FxRateSnapshot fxRateSnapshot = createFxRateSnapshot();
        BusinessException exception =
                new BusinessException(TransactionLimitErrorCode.ANNUAL_REMITTANCE_LIMIT_EXCEEDED);

        when(recipientRepository.findByIdAndUserIdAndDeletedAtIsNull(request.recipientId(), userId))
                .thenReturn(Optional.of(recipient));
        when(exchangeRateProvider.getLatestRate("USD", "KRW"))
                .thenReturn(Optional.of(fxRateSnapshot));
        doThrow(exception).when(remittanceValidator).validateLimits(userId, new BigDecimal("740.00074000"));

        // when & then
        assertThatThrownBy(() -> remittanceTransactionService.createQuote(userId, request))
                .isSameAs(exception);

        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("성공: 송금 주문을 생성하고 가상계좌를 발급한다")
    void createTransfer_success() {
        // given
        Long userId = 1L;
        Long transferId = 10L;
        Long virtualAccountId = 20L;
        int currentYear = LocalDate.now(ZoneId.of("Asia/Seoul")).getYear();

        RemittanceTransactionCreateRequest request = createRequest();
        RemittanceQuoteSnapshot quote = createQuote();
        Recipient recipient = createRecipient(userId);
        UserAnnualUsage annualUsage = createAnnualUsage(userId, currentYear, BigDecimal.ZERO);
        String idempotencyKey = "idempotency-key";

        when(remittanceTransactionRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty());
        when(remittanceQuoteProvider.getQuote(request.quoteId())).thenReturn(quote);
        when(recipientRepository.findByIdAndUserIdAndDeletedAtIsNull(quote.recipientId(), userId))
                .thenReturn(Optional.of(recipient));
        when(transactionLimitRepository.findByLimitTypeAndTierAndCurrencyCodeAndIsActiveTrue(
                LimitType.ANNUAL_REMITTANCE,
                LimitTier.STANDARD,
                "USD"
        )).thenReturn(Optional.of(createLimit(
                LimitType.ANNUAL_REMITTANCE,
                new BigDecimal("100000.00000000")
        )));
        when(userAnnualUsageRepository.findByUserIdAndYearForUpdate(userId, currentYear))
                .thenReturn(Optional.of(annualUsage));
        when(remittanceTransactionRepository.save(any(RemittanceTransaction.class)))
                .thenAnswer(invocation -> {
                    RemittanceTransaction remittanceTransaction = invocation.getArgument(0);
                    ReflectionTestUtils.setField(remittanceTransaction, "id", transferId);
                    return remittanceTransaction;
                });
        when(virtualAccountRepository.save(any(VirtualAccount.class)))
                .thenAnswer(invocation -> {
                    VirtualAccount virtualAccount = invocation.getArgument(0);
                    ReflectionTestUtils.setField(virtualAccount, "id", virtualAccountId);
                    return virtualAccount;
                });

        // when
        RemittanceTransactionCreateResponse response =
                remittanceTransactionService.createTransfer(userId, request, idempotencyKey);

        // then
        assertThat(response.transferId()).isEqualTo(transferId);
        assertThat(response.status()).isEqualTo(TransferStatus.PENDING);
        assertThat(response.virtualAccount().bankName()).isEqualTo("하나은행");
        assertThat(response.virtualAccount().amount()).isEqualTo(new BigDecimal("1008000"));
        assertThat(response.virtualAccount().expiredAt()).isNotNull();

        ArgumentCaptor<RemittanceTransaction> transactionCaptor =
                ArgumentCaptor.forClass(RemittanceTransaction.class);
        verify(remittanceTransactionRepository).save(transactionCaptor.capture());
        RemittanceTransaction savedTransaction = transactionCaptor.getValue();

        assertThat(savedTransaction.getUserId()).isEqualTo(userId);
        assertThat(savedTransaction.getRecipientId()).isEqualTo(quote.recipientId());
        assertThat(savedTransaction.getReason()).isEqualTo(RemittanceReason.LIVING_EXPENSES.name());
        assertThat(savedTransaction.getReasonDetail()).isEqualTo("생활비 송금");
        assertThat(savedTransaction.getStatus()).isEqualTo(TransferStatus.PENDING);
        verify(remittanceValidator).validateLimits(userId, quote.amountUsd());
        verify(userAnnualUsageRepository).findByUserIdAndYearForUpdate(userId, currentYear);
        assertThat(annualUsage.getAnnualUsedUsd()).isEqualByComparingTo(quote.amountUsd());

        ArgumentCaptor<VirtualAccount> virtualAccountCaptor =
                ArgumentCaptor.forClass(VirtualAccount.class);
        verify(virtualAccountRepository).save(virtualAccountCaptor.capture());
        VirtualAccount savedVirtualAccount = virtualAccountCaptor.getValue();

        assertThat(savedVirtualAccount.getUserId()).isEqualTo(userId);
        assertThat(savedVirtualAccount.getRemittanceTransactionId()).isEqualTo(transferId);
        assertThat(savedVirtualAccount.getExpectedAmount()).isEqualByComparingTo(new BigDecimal("1008000.00"));
        assertThat(savedVirtualAccount.getRefType()).isEqualTo("REMITTANCE");
        assertThat(savedVirtualAccount.getRefId()).isEqualTo(String.valueOf(transferId));
    }

    @Test
    @DisplayName("실패: quoteId에 해당하는 송금 견적이 없으면 예외가 발생한다")
    void createTransfer_fail_quoteNotFound() {
        // given
        Long userId = 1L;
        RemittanceTransactionCreateRequest request = createRequest();
        String idempotencyKey = "idempotency-key";

        when(remittanceTransactionRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty());
        when(remittanceQuoteProvider.getQuote(request.quoteId()))
                .thenThrow(new BusinessException(RemittanceTransactionErrorCode.QUOTE_NOT_FOUND));

        // when & then
        assertThatThrownBy(() -> remittanceTransactionService.createTransfer(userId, request, idempotencyKey))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(RemittanceTransactionErrorCode.QUOTE_NOT_FOUND);

        verifyNoInteractions(recipientRepository);
        verify(remittanceTransactionRepository, never()).save(any(RemittanceTransaction.class));
        verifyNoInteractions(virtualAccountRepository);
    }

    @Test
    @DisplayName("실패: 송금 한도를 초과하면 송금 주문과 가상계좌를 생성하지 않는다")
    void createTransfer_fail_limitExceeded() {
        // given
        Long userId = 1L;
        RemittanceTransactionCreateRequest request = createRequest();
        RemittanceQuoteSnapshot quote = createQuote();
        Recipient recipient = createRecipient(userId);
        BusinessException exception =
                new BusinessException(TransactionLimitErrorCode.ANNUAL_REMITTANCE_LIMIT_EXCEEDED);
        String idempotencyKey = "idempotency-key";

        when(remittanceTransactionRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty());
        when(remittanceQuoteProvider.getQuote(request.quoteId())).thenReturn(quote);
        when(recipientRepository.findByIdAndUserIdAndDeletedAtIsNull(quote.recipientId(), userId))
                .thenReturn(Optional.of(recipient));
        doThrow(exception).when(remittanceValidator).validateLimits(userId, quote.amountUsd());

        // when & then
        assertThatThrownBy(() -> remittanceTransactionService.createTransfer(userId, request, idempotencyKey))
                .isSameAs(exception);

        verify(remittanceTransactionRepository, never()).save(any(RemittanceTransaction.class));
        verifyNoInteractions(virtualAccountRepository);
    }

    @Test
    @DisplayName("실패: 선택한 수취인이 로그인한 사용자의 수취인이 아니면 예외가 발생한다")
    void createTransfer_fail_recipientNotFound() {
        // given
        Long userId = 1L;
        RemittanceTransactionCreateRequest request = createRequest();
        RemittanceQuoteSnapshot quote = createQuote();
        String idempotencyKey = "idempotency-key";

        when(remittanceTransactionRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty());
        when(remittanceQuoteProvider.getQuote(request.quoteId())).thenReturn(quote);
        when(recipientRepository.findByIdAndUserIdAndDeletedAtIsNull(quote.recipientId(), userId))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> remittanceTransactionService.createTransfer(userId, request, idempotencyKey))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(RecipientErrorCode.RECIPIENT_NOT_FOUND);

        verify(remittanceTransactionRepository, never()).save(any(RemittanceTransaction.class));
        verifyNoInteractions(virtualAccountRepository);
    }

    @Test
    @DisplayName("실패: 삭제된 수취인으로는 송금 주문을 생성할 수 없다")
    void createTransfer_fail_deletedRecipient() {
        // given
        Long userId = 1L;
        RemittanceTransactionCreateRequest request = createRequest();
        RemittanceQuoteSnapshot quote = createQuote();
        String idempotencyKey = "idempotency-key";

        when(remittanceTransactionRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty());
        when(remittanceQuoteProvider.getQuote(request.quoteId())).thenReturn(quote);
        when(recipientRepository.findByIdAndUserIdAndDeletedAtIsNull(quote.recipientId(), userId))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> remittanceTransactionService.createTransfer(userId, request, idempotencyKey))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(RecipientErrorCode.RECIPIENT_NOT_FOUND);

        verify(remittanceTransactionRepository, never()).save(any(RemittanceTransaction.class));
        verifyNoInteractions(virtualAccountRepository);
    }

    @Test
    @DisplayName("성공: 동일 Idempotency-Key 주문이 이미 있으면 기존 주문과 가상계좌를 반환한다")
    void createTransfer_duplicateIdempotencyKey_returnsExistingTransfer() {
        // given
        Long userId = 1L;
        Long transferId = 10L;
        String idempotencyKey = "idempotency-key";
        RemittanceTransactionCreateRequest request = createRequest();
        RemittanceQuoteSnapshot quote = createQuote();
        RemittanceTransaction existingTransaction = createPendingTransaction(userId, transferId);
        VirtualAccount existingVirtualAccount =
                createVirtualAccount(userId, transferId, LocalDateTime.now().plusMinutes(30));

        when(remittanceTransactionRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.of(existingTransaction));
        when(remittanceQuoteProvider.getQuote(request.quoteId())).thenReturn(quote);
        when(virtualAccountRepository.findByRemittanceTransactionId(transferId))
                .thenReturn(Optional.of(existingVirtualAccount));

        // when
        RemittanceTransactionCreateResponse response =
                remittanceTransactionService.createTransfer(userId, request, idempotencyKey);

        // then
        assertThat(response.transferId()).isEqualTo(transferId);
        assertThat(response.status()).isEqualTo(TransferStatus.PENDING);
        verifyNoInteractions(recipientRepository);
        verifyNoInteractions(remittanceValidator);
        verify(remittanceTransactionRepository, never()).save(any(RemittanceTransaction.class));
        verify(virtualAccountRepository, never()).save(any(VirtualAccount.class));
    }

    @Test
    @DisplayName("실패: 동일 Idempotency-Key로 다른 송금 요청을 보내면 예외가 발생한다")
    void createTransfer_fail_invalidIdempotencyRequest() {
        // given
        Long userId = 1L;
        Long transferId = 10L;
        String idempotencyKey = "idempotency-key";
        RemittanceTransactionCreateRequest request = createRequest();
        RemittanceQuoteSnapshot quote = new RemittanceQuoteSnapshot(
                "TQUOTE-001",
                1L,
                "KRW",
                new BigDecimal("2000000.00"),
                "USD",
                new BigDecimal("1473.04"),
                new BigDecimal("1351.00000000"),
                new BigDecimal("13000.00"),
                new BigDecimal("2000000.00"),
                new BigDecimal("1473.04")
        );
        RemittanceTransaction existingTransaction = createPendingTransaction(userId, transferId);

        when(remittanceTransactionRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.of(existingTransaction));
        when(remittanceQuoteProvider.getQuote(request.quoteId())).thenReturn(quote);

        // when & then
        assertThatThrownBy(() -> remittanceTransactionService.createTransfer(userId, request, idempotencyKey))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(RemittanceTransactionErrorCode.INVALID_IDEMPOTENCY_REQUEST);

        verifyNoInteractions(recipientRepository);
        verifyNoInteractions(remittanceValidator);
        verify(remittanceTransactionRepository, never()).save(any(RemittanceTransaction.class));
        verifyNoInteractions(virtualAccountRepository);
    }

    @Test
    @DisplayName("실패: 다른 사용자가 이미 사용한 Idempotency-Key이면 충돌 예외가 발생한다")
    void createTransfer_fail_idempotencyKeyConflict() {
        // given
        Long userId = 1L;
        Long otherUserId = 2L;
        Long transferId = 10L;
        String idempotencyKey = "idempotency-key";
        RemittanceTransactionCreateRequest request = createRequest();
        RemittanceTransaction existingTransaction = createPendingTransaction(otherUserId, transferId);

        when(remittanceTransactionRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.of(existingTransaction));

        // when & then
        assertThatThrownBy(() -> remittanceTransactionService.createTransfer(userId, request, idempotencyKey))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(RemittanceTransactionErrorCode.IDEMPOTENCY_KEY_CONFLICT);

        verifyNoInteractions(remittanceQuoteProvider);
        verifyNoInteractions(recipientRepository);
        verify(remittanceTransactionRepository, never()).save(any(RemittanceTransaction.class));
        verifyNoInteractions(virtualAccountRepository);
    }

    @Test
    @DisplayName("성공: Mock 입금 확인 시 모의계좌를 차감하고 송금 거래를 FUNDED로 변경한다")
    void mockFundTransfer_success() {
        // given
        Long userId = 1L;
        Long transferId = 10L;
        Long sourceMockAccountId = 30L;
        RemittanceTransaction remittanceTransaction = createPendingTransaction(userId, transferId);
        VirtualAccount virtualAccount = createVirtualAccount(userId, transferId, LocalDateTime.now().plusMinutes(30));

        when(remittanceTransactionRepository.findByIdForUpdate(transferId))
                .thenReturn(Optional.of(remittanceTransaction));
        when(virtualAccountRepository.findByRemittanceTransactionId(transferId))
                .thenReturn(Optional.of(virtualAccount));
        when(mockBankAccountService.withdrawForRemittance(
                eq(userId),
                eq("JRN-TRF-" + transferId + "-FUND"),
                eq(new BigDecimal("1008000.00")),
                eq("KRW"),
                eq(String.valueOf(transferId))
        )).thenReturn(sourceMockAccountId);

        // when
        RemittanceMockFundedResponse response =
                remittanceTransactionService.mockFundTransfer(userId, transferId);

        // then
        assertThat(response.transferId()).isEqualTo(transferId);
        assertThat(response.status()).isEqualTo(TransferStatus.FUNDED);
        assertThat(response.virtualAccountStatus()).isEqualTo(VirtualAccountStatus.PAID);
        assertThat(response.fundedAt()).isNotNull();
        assertThat(remittanceTransaction.getStatus()).isEqualTo(TransferStatus.FUNDED);
        assertThat(remittanceTransaction.getSourceMockAccountId()).isEqualTo(sourceMockAccountId);
        assertThat(virtualAccount.getStatus()).isEqualTo(VirtualAccountStatus.PAID);
        assertThat(virtualAccount.getPaidAt()).isNotNull();

        verify(companyPoolService).depositForRemittance(
                "JRN-TRF-" + transferId + "-FUND",
                "KRW",
                new BigDecimal("1008000.00"),
                transferId
        );
        ArgumentCaptor<RemittanceFundedEvent> eventCaptor =
                ArgumentCaptor.forClass(RemittanceFundedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        RemittanceFundedEvent event = eventCaptor.getValue();

        assertThat(event.transferId()).isEqualTo(transferId);
        assertThat(event.userId()).isEqualTo(userId);
        assertThat(event.amountKrw()).isEqualByComparingTo(new BigDecimal("1008000.00"));
        assertThat(event.receiveCurrency()).isEqualTo("USD");
        assertThat(event.receiveAmount()).isEqualByComparingTo(new BigDecimal("736.52"));
        assertThat(event.fundedAt()).isNotNull();
    }

    @Test
    @DisplayName("실패: 송금 거래가 없으면 Mock 입금 확인을 처리하지 않는다")
    void mockFundTransfer_fail_transactionNotFound() {
        // given
        Long userId = 1L;
        Long transferId = 10L;

        when(remittanceTransactionRepository.findByIdForUpdate(transferId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> remittanceTransactionService.mockFundTransfer(userId, transferId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(RemittanceTransactionErrorCode.REMITTANCE_TRANSACTION_NOT_FOUND);

        verifyNoInteractions(virtualAccountRepository);
        verifyNoInteractions(mockBankAccountService);
        verifyNoInteractions(companyPoolService);
    }

    @Test
    @DisplayName("실패: PENDING 상태가 아니면 Mock 입금 확인을 처리하지 않는다")
    void mockFundTransfer_fail_invalidTransferStatus() {
        // given
        Long userId = 1L;
        Long transferId = 10L;
        RemittanceTransaction remittanceTransaction = createPendingTransaction(userId, transferId);
        remittanceTransaction.fund(30L);

        when(remittanceTransactionRepository.findByIdForUpdate(transferId))
                .thenReturn(Optional.of(remittanceTransaction));

        // when & then
        assertThatThrownBy(() -> remittanceTransactionService.mockFundTransfer(userId, transferId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(RemittanceTransactionErrorCode.INVALID_REMITTANCE_TRANSACTION_STATUS);

        verifyNoInteractions(virtualAccountRepository);
        verifyNoInteractions(mockBankAccountService);
        verifyNoInteractions(companyPoolService);
    }

    @Test
    @DisplayName("실패: 연결된 가상계좌가 없으면 Mock 입금 확인을 처리하지 않는다")
    void mockFundTransfer_fail_virtualAccountNotFound() {
        // given
        Long userId = 1L;
        Long transferId = 10L;
        RemittanceTransaction remittanceTransaction = createPendingTransaction(userId, transferId);

        when(remittanceTransactionRepository.findByIdForUpdate(transferId))
                .thenReturn(Optional.of(remittanceTransaction));
        when(virtualAccountRepository.findByRemittanceTransactionId(transferId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> remittanceTransactionService.mockFundTransfer(userId, transferId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(RemittanceTransactionErrorCode.VIRTUAL_ACCOUNT_NOT_FOUND);

        verifyNoInteractions(mockBankAccountService);
        verifyNoInteractions(companyPoolService);
    }

    @Test
    @DisplayName("실패: 가상계좌가 ISSUED 상태가 아니면 Mock 입금 확인을 처리하지 않는다")
    void mockFundTransfer_fail_invalidVirtualAccountStatus() {
        // given
        Long userId = 1L;
        Long transferId = 10L;
        RemittanceTransaction remittanceTransaction = createPendingTransaction(userId, transferId);
        VirtualAccount virtualAccount = createVirtualAccount(userId, transferId, LocalDateTime.now().plusMinutes(30));
        virtualAccount.pay(LocalDateTime.now());

        when(remittanceTransactionRepository.findByIdForUpdate(transferId))
                .thenReturn(Optional.of(remittanceTransaction));
        when(virtualAccountRepository.findByRemittanceTransactionId(transferId))
                .thenReturn(Optional.of(virtualAccount));

        // when & then
        assertThatThrownBy(() -> remittanceTransactionService.mockFundTransfer(userId, transferId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(RemittanceTransactionErrorCode.INVALID_VIRTUAL_ACCOUNT_STATUS);

        verifyNoInteractions(mockBankAccountService);
        verifyNoInteractions(companyPoolService);
    }

    @Test
    @DisplayName("실패: 가상계좌가 만료되면 Mock 입금 확인을 처리하지 않는다")
    void mockFundTransfer_fail_virtualAccountExpired() {
        // given
        Long userId = 1L;
        Long transferId = 10L;
        RemittanceTransaction remittanceTransaction = createPendingTransaction(userId, transferId);
        VirtualAccount virtualAccount = createVirtualAccount(userId, transferId, LocalDateTime.now().minusMinutes(1));

        when(remittanceTransactionRepository.findByIdForUpdate(transferId))
                .thenReturn(Optional.of(remittanceTransaction));
        when(virtualAccountRepository.findByRemittanceTransactionId(transferId))
                .thenReturn(Optional.of(virtualAccount));

        // when & then
        assertThatThrownBy(() -> remittanceTransactionService.mockFundTransfer(userId, transferId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(RemittanceTransactionErrorCode.VIRTUAL_ACCOUNT_EXPIRED);

        verifyNoInteractions(mockBankAccountService);
        verifyNoInteractions(companyPoolService);
    }

    @Test
    @DisplayName("성공: 입금 대기 중인 송금 주문을 취소하고 선점한 연간 한도를 복구한다")
    void cancelTransfer_success() {
        // given
        Long userId = 1L;
        Long transferId = 10L;
        int currentYear = LocalDate.now(ZoneId.of("Asia/Seoul")).getYear();
        RemittanceTransaction remittanceTransaction = createPendingTransaction(userId, transferId);
        VirtualAccount virtualAccount = createVirtualAccount(userId, transferId, LocalDateTime.now().plusMinutes(30));
        UserAnnualUsage annualUsage = createAnnualUsage(
                userId,
                currentYear,
                new BigDecimal("1000.00")
        );

        when(remittanceTransactionRepository.findByIdForUpdate(transferId))
                .thenReturn(Optional.of(remittanceTransaction));
        when(virtualAccountRepository.findByRemittanceTransactionId(transferId))
                .thenReturn(Optional.of(virtualAccount));
        when(userAnnualUsageRepository.findByUserIdAndYearForUpdate(userId, currentYear))
                .thenReturn(Optional.of(annualUsage));

        // when
        RemittanceCancelResponse response = remittanceTransactionService.cancelTransfer(userId, transferId);

        // then
        assertThat(response.transferId()).isEqualTo(transferId);
        assertThat(response.status()).isEqualTo(TransferStatus.CANCELED);
        assertThat(response.virtualAccountStatus()).isEqualTo(VirtualAccountStatus.CANCELED);
        assertThat(remittanceTransaction.getStatus()).isEqualTo(TransferStatus.CANCELED);
        assertThat(virtualAccount.getStatus()).isEqualTo(VirtualAccountStatus.CANCELED);
        assertThat(annualUsage.getAnnualUsedUsd()).isEqualByComparingTo(new BigDecimal("263.48"));
    }

    @Test
    @DisplayName("성공: 송금 생성 연도 기준으로 선점한 연간 한도를 복구한다")
    void cancelTransfer_success_releaseLimitByCreatedYear() {
        // given
        Long userId = 1L;
        Long transferId = 10L;
        int currentYear = LocalDate.now(ZoneId.of("Asia/Seoul")).getYear();
        int reservedYear = currentYear - 1;
        RemittanceTransaction remittanceTransaction = createPendingTransaction(userId, transferId);
        ReflectionTestUtils.setField(
                remittanceTransaction,
                "createdAt",
                LocalDateTime.of(reservedYear, 12, 31, 23, 50)
        );
        VirtualAccount virtualAccount = createVirtualAccount(userId, transferId, LocalDateTime.now().plusMinutes(30));
        UserAnnualUsage annualUsage = createAnnualUsage(
                userId,
                reservedYear,
                new BigDecimal("1000.00")
        );

        when(remittanceTransactionRepository.findByIdForUpdate(transferId))
                .thenReturn(Optional.of(remittanceTransaction));
        when(virtualAccountRepository.findByRemittanceTransactionId(transferId))
                .thenReturn(Optional.of(virtualAccount));
        when(userAnnualUsageRepository.findByUserIdAndYearForUpdate(userId, reservedYear))
                .thenReturn(Optional.of(annualUsage));

        // when
        RemittanceCancelResponse response = remittanceTransactionService.cancelTransfer(userId, transferId);

        // then
        assertThat(response.status()).isEqualTo(TransferStatus.CANCELED);
        assertThat(annualUsage.getAnnualUsedUsd()).isEqualByComparingTo(new BigDecimal("263.48"));
        verify(userAnnualUsageRepository).findByUserIdAndYearForUpdate(userId, reservedYear);
        verify(userAnnualUsageRepository, never()).findByUserIdAndYearForUpdate(userId, currentYear);
    }

    @Test
    @DisplayName("실패: 입금 대기 상태가 아니면 송금 주문을 취소할 수 없다")
    void cancelTransfer_fail_notPending() {
        // given
        Long userId = 1L;
        Long transferId = 10L;
        RemittanceTransaction remittanceTransaction = createPendingTransaction(userId, transferId);
        remittanceTransaction.fund(30L);

        when(remittanceTransactionRepository.findByIdForUpdate(transferId))
                .thenReturn(Optional.of(remittanceTransaction));

        // when & then
        assertThatThrownBy(() -> remittanceTransactionService.cancelTransfer(userId, transferId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(RemittanceTransactionErrorCode.REMITTANCE_CANCEL_NOT_ALLOWED);

        verifyNoInteractions(virtualAccountRepository);
        verifyNoInteractions(userAnnualUsageRepository);
    }

    @Test
    @DisplayName("성공: 입금 기한이 지난 송금 주문을 자동 취소하고 선점한 한도를 복구한다")
    void expirePendingTransfers_success() {
        // given
        Long userId = 1L;
        Long transferId = 10L;
        LocalDateTime now = LocalDateTime.now();
        int reservedYear = LocalDate.now(ZoneId.of("Asia/Seoul")).getYear();
        RemittanceTransaction remittanceTransaction = createPendingTransaction(userId, transferId);
        VirtualAccount virtualAccount = createVirtualAccount(userId, transferId, now.minusMinutes(1));
        UserAnnualUsage annualUsage = createAnnualUsage(
                userId,
                reservedYear,
                new BigDecimal("1000.00")
        );

        when(virtualAccountRepository.findByStatusAndExpiredAtLessThanEqual(VirtualAccountStatus.ISSUED, now))
                .thenReturn(List.of(virtualAccount));
        when(remittanceTransactionRepository.findByIdForUpdate(transferId))
                .thenReturn(Optional.of(remittanceTransaction));
        when(userAnnualUsageRepository.findByUserIdAndYearForUpdate(userId, reservedYear))
                .thenReturn(Optional.of(annualUsage));

        // when
        int expiredCount = remittanceTransactionService.expirePendingTransfers(now);

        // then
        assertThat(expiredCount).isEqualTo(1);
        assertThat(remittanceTransaction.getStatus()).isEqualTo(TransferStatus.CANCELED);
        assertThat(virtualAccount.getStatus()).isEqualTo(VirtualAccountStatus.EXPIRED);
        assertThat(annualUsage.getAnnualUsedUsd()).isEqualByComparingTo(new BigDecimal("263.48"));
    }

    private RemittanceTransactionCreateRequest createRequest() {
        return new RemittanceTransactionCreateRequest(
                "TQUOTE-001",
                RemittanceReason.LIVING_EXPENSES,
                "생활비 송금"
        );
    }

    private RemittanceTransactionQuoteRequest createQuoteRequest() {
        return new RemittanceTransactionQuoteRequest(
                1L,
                new BigDecimal("1000000"),
                RemittanceReason.LIVING_EXPENSES
        );
    }

    private FxRateSnapshot createFxRateSnapshot() {
        return new FxRateSnapshot(
                "USD",
                "KRW",
                new BigDecimal("1350.00000000"),
                new BigDecimal("0.001"),
                LocalDateTime.now()
        );
    }

    @Test
    @DisplayName("성공: 송금 내역 목록을 최신순으로 조회한다")
    void getTransfers_success() {
        // given
        Long userId = 1L;
        Long transferId = 10L;
        RemittanceTransaction remittanceTransaction = createPendingTransaction(userId, transferId);
        Recipient recipient = createRecipient(userId);

        when(remittanceTransactionRepository.findByUserId(eq(userId), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(remittanceTransaction), PageRequest.of(0, 20), 1));
        when(recipientRepository.findById(remittanceTransaction.getRecipientId()))
                .thenReturn(Optional.of(recipient));

        // when
        RemittanceTransactionPageResponse response =
                remittanceTransactionService.getTransfers(userId, 0, 20);

        // then
        assertThat(response.data()).hasSize(1);
        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(20);
        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.totalPages()).isEqualTo(1);
        assertThat(response.data().getFirst().transferId()).isEqualTo(transferId);
        assertThat(response.data().getFirst().recipientName()).isEqualTo("John Doe");
        assertThat(response.data().getFirst().recipientCountryCode()).isEqualTo("US");
        assertThat(response.data().getFirst().recipientCurrencyCode()).isEqualTo("USD");
        assertThat(response.data().getFirst().recipientBankName()).isEqualTo("Chase Bank");
        assertThat(response.data().getFirst().sendAmount()).isEqualByComparingTo(new BigDecimal("1000000"));
        assertThat(response.data().getFirst().sendCurrency()).isEqualTo("KRW");
        assertThat(response.data().getFirst().receiveAmount()).isEqualByComparingTo(new BigDecimal("736.52"));
        assertThat(response.data().getFirst().receiveCurrency()).isEqualTo("USD");
        assertThat(response.data().getFirst().appliedRate()).isEqualByComparingTo(new BigDecimal("1351.00000000"));
        assertThat(response.data().getFirst().feeAmount()).isEqualByComparingTo(new BigDecimal("8000"));
        assertThat(response.data().getFirst().status()).isEqualTo(TransferStatus.PENDING);
    }

    @Test
    @DisplayName("성공: 특정 송금 내역을 상세 조회한다")
    void getTransfer_success() {
        // given
        Long userId = 1L;
        Long transferId = 10L;
        RemittanceTransaction remittanceTransaction = createPendingTransaction(userId, transferId);
        Recipient recipient = createRecipient(userId);
        VirtualAccount virtualAccount = createVirtualAccount(userId, transferId, LocalDateTime.now().plusMinutes(30));

        when(remittanceTransactionRepository.findByIdAndUserId(transferId, userId))
                .thenReturn(Optional.of(remittanceTransaction));
        when(recipientRepository.findById(remittanceTransaction.getRecipientId()))
                .thenReturn(Optional.of(recipient));
        when(virtualAccountRepository.findByRemittanceTransactionId(transferId))
                .thenReturn(Optional.of(virtualAccount));

        // when
        RemittanceTransactionDetailResponse response =
                remittanceTransactionService.getTransfer(userId, transferId);

        // then
        assertThat(response.transferId()).isEqualTo(transferId);
        assertThat(response.status()).isEqualTo(TransferStatus.PENDING);
        assertThat(response.recipient().name()).isEqualTo("John Doe");
        assertThat(response.recipient().bankName()).isEqualTo("Chase Bank");
        assertThat(response.recipient().accountNumber()).isEqualTo("1234567890");
        assertThat(response.sendAmountKrw()).isEqualByComparingTo(new BigDecimal("1000000"));
        assertThat(response.receiveAmountUsd()).isEqualByComparingTo(new BigDecimal("736.52"));
        assertThat(response.appliedRate()).isEqualByComparingTo(new BigDecimal("1351.00000000"));
        assertThat(response.totalFee()).isEqualByComparingTo(new BigDecimal("8000"));
        assertThat(response.virtualAccount().bankName()).isEqualTo("하나은행");
        assertThat(response.virtualAccount().accountNumber()).isEqualTo("123-456789-123456");
        assertThat(response.virtualAccount().amount()).isEqualByComparingTo(new BigDecimal("1008000"));
        assertThat(response.createdAt()).isEqualTo(remittanceTransaction.getCreatedAt());
    }

    @Test
    @DisplayName("성공: 가상계좌가 없어도 특정 송금 내역을 상세 조회한다")
    void getTransfer_success_withoutVirtualAccount() {
        // given
        Long userId = 1L;
        Long transferId = 10L;
        RemittanceTransaction remittanceTransaction = createPendingTransaction(userId, transferId);
        Recipient recipient = createRecipient(userId);

        when(remittanceTransactionRepository.findByIdAndUserId(transferId, userId))
                .thenReturn(Optional.of(remittanceTransaction));
        when(recipientRepository.findById(remittanceTransaction.getRecipientId()))
                .thenReturn(Optional.of(recipient));
        when(virtualAccountRepository.findByRemittanceTransactionId(transferId))
                .thenReturn(Optional.empty());

        // when
        RemittanceTransactionDetailResponse response =
                remittanceTransactionService.getTransfer(userId, transferId);

        // then
        assertThat(response.transferId()).isEqualTo(transferId);
        assertThat(response.virtualAccount()).isNull();
    }

    @Test
    @DisplayName("실패: 특정 송금 내역을 찾을 수 없으면 예외가 발생한다")
    void getTransfer_fail_notFound() {
        // given
        Long userId = 1L;
        Long transferId = 10L;

        when(remittanceTransactionRepository.findByIdAndUserId(transferId, userId))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> remittanceTransactionService.getTransfer(userId, transferId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(RemittanceTransactionErrorCode.REMITTANCE_TRANSACTION_NOT_FOUND);
    }

    private RemittanceQuoteSnapshot createQuote() {
        return new RemittanceQuoteSnapshot(
                "TQUOTE-001",
                1L,
                "KRW",
                new BigDecimal("1000000.00"),
                "USD",
                new BigDecimal("736.52"),
                new BigDecimal("1351.00000000"),
                new BigDecimal("8000.00"),
                new BigDecimal("1000000.00"),
                new BigDecimal("736.52")
        );
    }

    private Recipient createRecipient(Long userId) {
        Recipient recipient = Recipient.create(
                userId,
                "John Doe",
                "US",
                "USD",
                "Chase Bank",
                "1234567890"
        );
        ReflectionTestUtils.setField(recipient, "id", 1L);

        return recipient;
    }

    private UserAnnualUsage createAnnualUsage(Long userId, int year, BigDecimal usedUsd) {
        User user = User.create(
                "remittance-user@example.com",
                "encoded-password",
                "송금사용자"
        );
        ReflectionTestUtils.setField(user, "id", userId);

        UserAnnualUsage usage = UserAnnualUsage.create(user, year);
        usage.addUsage(usedUsd);

        return usage;
    }

    private TransactionLimit createLimit(LimitType limitType, BigDecimal limitAmount) {
        return TransactionLimit.create(
                limitType,
                LimitTier.STANDARD,
                "USD",
                limitAmount
        );
    }

    private RemittanceTransaction createPendingTransaction(Long userId, Long transferId) {
        RemittanceTransaction remittanceTransaction = RemittanceTransaction.create(
                userId,
                1L,
                null,
                RemittanceMethod.BANK_TRANSFER.name(),
                null,
                null,
                null,
                null,
                null,
                "KRW",
                new BigDecimal("1000000.00"),
                "USD",
                new BigDecimal("736.52"),
                new BigDecimal("1351.00000000"),
                new BigDecimal("8000.00"),
                new BigDecimal("1000000.00"),
                new BigDecimal("736.52"),
                RemittanceReason.LIVING_EXPENSES.name(),
                "생활비 송금",
                "idempotency-key"
        );
        ReflectionTestUtils.setField(remittanceTransaction, "id", transferId);

        return remittanceTransaction;
    }

    private VirtualAccount createVirtualAccount(Long userId, Long transferId, LocalDateTime expiredAt) {
        return VirtualAccount.create(
                userId,
                transferId,
                "하나은행",
                "123-456789-123456",
                new BigDecimal("1008000.00"),
                "REMITTANCE",
                String.valueOf(transferId),
                LocalDateTime.now().minusMinutes(10),
                expiredAt
        );
    }
}
