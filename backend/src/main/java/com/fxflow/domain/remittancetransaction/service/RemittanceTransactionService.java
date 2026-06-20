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
import com.fxflow.domain.user.errorcode.UserErrorCode;
import com.fxflow.domain.user.repository.UserRepository;
import com.fxflow.domain.userlimitusage.entity.UserAnnualUsage;
import com.fxflow.domain.userlimitusage.repository.UserAnnualUsageRepository;
import com.fxflow.global.exception.BusinessException;
import com.fxflow.global.fx.ExchangeRateProvider;
import com.fxflow.global.fx.FxRateSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RemittanceTransactionService {

    private static final String KRW = "KRW";
    private static final String USD = "USD";
    private static final LimitTier STANDARD_TIER = LimitTier.STANDARD;
    private static final String DEFAULT_VIRTUAL_ACCOUNT_BANK_NAME = "하나은행";
    private static final String REMITTANCE_REF_TYPE = "REMITTANCE";

    private final UserAnnualUsageRepository userAnnualUsageRepository;
    private final TransactionLimitRepository transactionLimitRepository;
    private final UserRepository userRepository;
    private final RecipientRepository recipientRepository;
    private final RemittanceTransactionRepository remittanceTransactionRepository;
    private final VirtualAccountRepository virtualAccountRepository;
    private final RemittanceQuoteProvider remittanceQuoteProvider;
    private final RemittanceValidator remittanceValidator;
    private final MockBankAccountService mockBankAccountService;
    private final CompanyPoolService companyPoolService;
    private final ApplicationEventPublisher eventPublisher;

    private static final BigDecimal FIXED_FEE_KRW = new BigDecimal("3000.00000000");
    private static final BigDecimal PERCENT_FEE_RATE = new BigDecimal("0.005");
    private static final long QUOTE_EXPIRATION_MINUTES = 5L;
    private static final String REMITTANCE_QUOTE_KEY_PREFIX = "remittance:quote:";

    private final ExchangeRateProvider exchangeRateProvider;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 유저의 해외송금 잔여 한도를 조회한다.
     */
    public RemittanceLimitResponse getRemittanceLimit(Long userId) {
        int currentYear = LocalDate.now(ZoneId.of("Asia/Seoul")).getYear();

        BigDecimal currentYearTotalUsd = userAnnualUsageRepository
                .findByUserIdAndYear(userId, currentYear)
                .map(UserAnnualUsage::getAnnualUsedUsd)
                .orElse(BigDecimal.ZERO);

        BigDecimal maxPerTransactionUsd = getLimitAmount(LimitType.PER_REMITTANCE);
        BigDecimal maxPerYearUsd = getLimitAmount(LimitType.ANNUAL_REMITTANCE);

        return RemittanceLimitResponse.of(
                maxPerTransactionUsd,
                maxPerYearUsd,
                currentYearTotalUsd
        );
    }

    /**
     * 로그인한 사용자의 해외송금 내역 목록을 최신순으로 조회한다.
     */
    public List<RemittanceTransactionSummaryResponse> getTransfers(Long userId) {
        return remittanceTransactionRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(remittanceTransaction -> RemittanceTransactionSummaryResponse.from(
                        remittanceTransaction,
                        getRecipientForHistory(remittanceTransaction.getRecipientId())
                ))
                .toList();
    }

    /**
     * 로그인한 사용자의 특정 해외송금 내역을 상세 조회한다.
     */
    public RemittanceTransactionDetailResponse getTransfer(Long userId, Long transferId) {
        RemittanceTransaction remittanceTransaction = remittanceTransactionRepository.findByIdAndUserId(
                        transferId,
                        userId
                )
                .orElseThrow(() -> new BusinessException(
                        RemittanceTransactionErrorCode.REMITTANCE_TRANSACTION_NOT_FOUND
                ));

        Recipient recipient = getRecipientForHistory(remittanceTransaction.getRecipientId());

        return RemittanceTransactionDetailResponse.from(remittanceTransaction, recipient);
    }

    /**
     * 송금 사유를 검증하고 송금 주문 생성 후 입금용 가상계좌를 발급한다.
     */
    @Transactional
    public RemittanceTransactionCreateResponse createTransfer(
            Long userId,
            RemittanceTransactionCreateRequest request,
            String idempotencyKey
    ) {
        validateIdempotencyKey(idempotencyKey);

        RemittanceTransaction existingTransaction = remittanceTransactionRepository
                .findByIdempotencyKey(idempotencyKey)
                .orElse(null);
        if (existingTransaction != null) {
            if (!existingTransaction.getUserId().equals(userId)) {
                throw new BusinessException(RemittanceTransactionErrorCode.IDEMPOTENCY_KEY_CONFLICT);
            }
            VirtualAccount existingVirtualAccount = getVirtualAccount(existingTransaction.getId());
            return RemittanceTransactionCreateResponse.of(existingTransaction, existingVirtualAccount);
        }

        RemittanceQuoteSnapshot quote = remittanceQuoteProvider.getQuote(request.quoteId());
        Recipient recipient = getRecipient(userId, quote.recipientId());

        // 견적의 USD 환산 금액으로 건당/연간 해외송금 한도를 검증한다.
        remittanceValidator.validateLimits(userId, quote.amountUsd());

        // 송금 주문 생성(PENDING) 시점에 연간 한도를 선점한다.
        // TRF-09는 조회만 담당하고, 실제 한도 정합성은 주문 생성 트랜잭션에서 보장한다.
        reserveAnnualRemittanceLimit(userId, quote.amountUsd());

        RemittanceTransaction remittanceTransaction = RemittanceTransaction.create(
                userId,
                quote.recipientId(),
                null,
                RemittanceMethod.BANK_TRANSFER.name(),
                null,
                null,
                null,
                null,
                null,
                quote.sendCurrency(),
                quote.sendAmount(),
                quote.receiveCurrency(),
                quote.receiveAmount(),
                quote.appliedRate(),
                quote.feeAmount(),
                quote.amountKrw(),
                quote.amountUsd(),
                request.reason().name(),
                request.reasonDetail(),
                idempotencyKey
        );

        RemittanceTransaction savedTransaction = remittanceTransactionRepository.save(remittanceTransaction);

        VirtualAccount virtualAccount = createVirtualAccount(userId, savedTransaction, quote.totalPaymentAmount());
        VirtualAccount savedVirtualAccount = virtualAccountRepository.save(virtualAccount);

        return RemittanceTransactionCreateResponse.of(savedTransaction, savedVirtualAccount);
    }

    /**
     * Mock 입금 확인을 처리하고 송금 주문을 입금 완료 상태로 변경한다.
     */
    @Transactional
    public RemittanceMockFundedResponse mockFundTransfer(Long userId, Long transferId) {
        RemittanceTransaction remittanceTransaction = remittanceTransactionRepository.findById(transferId)
                .orElseThrow(() -> new BusinessException(
                        RemittanceTransactionErrorCode.REMITTANCE_TRANSACTION_NOT_FOUND
                ));

        validateTransferOwner(userId, remittanceTransaction);
        validatePendingTransfer(remittanceTransaction);

        VirtualAccount virtualAccount = virtualAccountRepository
                .findByRemittanceTransactionId(remittanceTransaction.getId())
                .orElseThrow(() -> new BusinessException(
                        RemittanceTransactionErrorCode.VIRTUAL_ACCOUNT_NOT_FOUND
                ));

        validateIssuedVirtualAccount(virtualAccount);

        LocalDateTime paidAt = LocalDateTime.now();
        validateVirtualAccountNotExpired(virtualAccount, paidAt);

        String journalId = createFundJournalId(remittanceTransaction.getId());
        String refId = String.valueOf(remittanceTransaction.getId());
        BigDecimal krwAmount = virtualAccount.getExpectedAmount();

        Long sourceMockAccountId = mockBankAccountService.withdrawForRemittance(
                userId,
                journalId,
                krwAmount,
                KRW,
                refId
        );

        companyPoolService.depositForRemittance(journalId, KRW, krwAmount, remittanceTransaction.getId());

        remittanceTransaction.fund(sourceMockAccountId);
        virtualAccount.pay(paidAt);

        // 트랜잭션 커밋 이후 TRF-08 등 후속 처리가 이어질 수 있도록 입금 완료 이벤트를 발행한다.
        eventPublisher.publishEvent(RemittanceFundedEvent.of(remittanceTransaction, virtualAccount));

        return RemittanceMockFundedResponse.of(remittanceTransaction, virtualAccount);
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException(RemittanceTransactionErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        }
    }

    /**
     * 한도 정책에서 특정 한도 타입의 기준 금액을 조회한다.
     */
    private BigDecimal getLimitAmount(LimitType limitType) {
        return transactionLimitRepository
                .findByLimitTypeAndTierAndCurrencyCodeAndIsActiveTrue(
                        limitType,
                        STANDARD_TIER,
                        USD
                )
                .map(TransactionLimit::getLimitAmount)
                .orElseThrow(() -> new BusinessException(TransactionLimitErrorCode.LIMIT_POLICY_NOT_FOUND));
    }

    /**
     * 송금 주문 생성 시점에 연간 송금 한도를 선점한다.
     *
     * 정책:
     * - PENDING 주문 생성 시 annual_used_usd에 송금 USD 금액을 반영한다.
     * - 같은 유저/연도 사용량 row를 비관적 락으로 잠가 동시 주문의 한도 초과를 막는다.
     * - 이후 CANCELED / FAILED / EXPIRED 상태가 구현되면 선점한 한도를 복구해야 한다.
     */
    private void reserveAnnualRemittanceLimit(Long userId, BigDecimal amountUsd) {
        int currentYear = LocalDate.now(ZoneId.of("Asia/Seoul")).getYear();
        BigDecimal annualLimitUsd = getLimitAmount(LimitType.ANNUAL_REMITTANCE);

        UserAnnualUsage usage = userAnnualUsageRepository
                .findByUserIdAndYearForUpdate(userId, currentYear)
                .orElseGet(() -> createAnnualUsage(userId, currentYear));

        BigDecimal nextUsedUsd = usage.getAnnualUsedUsd().add(amountUsd);

        if (nextUsedUsd.compareTo(annualLimitUsd) > 0) {
            throw new BusinessException(TransactionLimitErrorCode.ANNUAL_REMITTANCE_LIMIT_EXCEEDED);
        }

        usage.addUsage(amountUsd);
    }

    /**
     * 해당 연도의 송금 한도 사용량 데이터가 없으면 새로 생성한다.
     * 최초 송금 주문 생성 시점에 만들어지며, 이후부터는 비관적 락 조회 대상이 된다.
     */
    private UserAnnualUsage createAnnualUsage(Long userId, int year) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        UserAnnualUsage usage = UserAnnualUsage.create(user, year);
        return userAnnualUsageRepository.save(usage);
    }

    /**
     * 송금 주문 생성 시 사용할 수취인을 조회한다.
     * 조회한 수취인 정보는 송금 당시 정보 보존을 위해 거래 스냅샷으로 저장한다.
     */
    private Recipient getRecipient(Long userId, Long recipientId) {
        return recipientRepository.findByIdAndUserIdAndDeletedAtIsNull(recipientId, userId)
                .orElseThrow(() -> new BusinessException(RecipientErrorCode.RECIPIENT_NOT_FOUND));
    }

    /**
     * 송금 내역 조회에 사용할 수취인을 조회한다.
     * Soft Delete 된 수취인도 과거 송금 내역에서는 보여야 하므로 deletedAt 조건을 걸지 않는다.
     */
    private Recipient getRecipientForHistory(Long recipientId) {
        return recipientRepository.findById(recipientId)
                .orElseThrow(() -> new BusinessException(RecipientErrorCode.RECIPIENT_NOT_FOUND));
    }

    /**
     * 송금 주문에 발급된 가상계좌를 조회한다.
     */
    private VirtualAccount getVirtualAccount(Long remittanceTransactionId) {
        return virtualAccountRepository
                .findByRemittanceTransactionId(remittanceTransactionId)
                .orElseThrow(() -> new BusinessException(
                        RemittanceTransactionErrorCode.VIRTUAL_ACCOUNT_NOT_FOUND
                ));
    }

    /**
     * 로그인한 사용자의 송금 거래인지 확인한다.
     */
    private void validateTransferOwner(Long userId, RemittanceTransaction remittanceTransaction) {
        if (!remittanceTransaction.getUserId().equals(userId)) {
            throw new BusinessException(RemittanceTransactionErrorCode.REMITTANCE_TRANSACTION_NOT_FOUND);
        }
    }

    /**
     * 입금 대기 상태의 송금 주문만 Mock 입금 확인을 허용한다.
     */
    private void validatePendingTransfer(RemittanceTransaction remittanceTransaction) {
        if (remittanceTransaction.getStatus() != TransferStatus.PENDING) {
            throw new BusinessException(RemittanceTransactionErrorCode.INVALID_REMITTANCE_TRANSACTION_STATUS);
        }
    }

    /**
     * 발급 완료 상태의 가상계좌만 입금 확인을 허용한다.
     */
    private void validateIssuedVirtualAccount(VirtualAccount virtualAccount) {
        if (virtualAccount.getStatus() != VirtualAccountStatus.ISSUED) {
            throw new BusinessException(RemittanceTransactionErrorCode.INVALID_VIRTUAL_ACCOUNT_STATUS);
        }
    }

    /**
     * 가상계좌 입금 기한이 지나지 않았는지 확인한다.
     */
    private void validateVirtualAccountNotExpired(VirtualAccount virtualAccount, LocalDateTime now) {
        if (!virtualAccount.getExpiredAt().isAfter(now)) {
            throw new BusinessException(RemittanceTransactionErrorCode.VIRTUAL_ACCOUNT_EXPIRED);
        }
    }

    /**
     * 송금 주문 입금을 위한 가상계좌 엔티티를 생성한다.
     */
    private VirtualAccount createVirtualAccount(
            Long userId,
            RemittanceTransaction remittanceTransaction,
            BigDecimal expectedAmount
    ) {
        LocalDateTime issuedAt = LocalDateTime.now();
        LocalDateTime expiredAt = issuedAt.plusHours(1);

        return VirtualAccount.create(
                userId,
                remittanceTransaction.getId(),
                DEFAULT_VIRTUAL_ACCOUNT_BANK_NAME,
                generateVirtualAccountNumber(),
                expectedAmount,
                REMITTANCE_REF_TYPE,
                String.valueOf(remittanceTransaction.getId()),
                issuedAt,
                expiredAt
        );
    }

    /**
     * 해외송금 입금 확인 단계의 원장 묶음 ID를 생성한다.
     */
    private String createFundJournalId(Long transferId) {
        return "JRN-TRF-" + transferId + "-FUND";
    }

    /**
     * 테스트용 가상계좌 번호를 생성한다.
     */
    private String generateVirtualAccountNumber() {
        int first = ThreadLocalRandom.current().nextInt(100, 1000);
        int second = ThreadLocalRandom.current().nextInt(100000, 1000000);
        int third = ThreadLocalRandom.current().nextInt(100000, 1000000);

        return first + "-" + second + "-" + third;
    }

    /**
     * 수취인, 송금 금액, 송금 사유를 기준으로 해외송금 견적을 산출하고 Redis에 저장한다.
     */
    public RemittanceTransactionQuoteResponse createQuote(
            Long userId,
            RemittanceTransactionQuoteRequest request
    ) {
        Recipient recipient = getRecipient(userId, request.recipientId());

        FxRateSnapshot fxRateSnapshot = exchangeRateProvider.getLatestRate(USD, KRW)
                .orElseThrow(() -> new BusinessException(
                        RemittanceTransactionErrorCode.REMITTANCE_EXCHANGE_RATE_NOT_FOUND
                ));

        BigDecimal exchangeRate = fxRateSnapshot.buyRate();
        BigDecimal sendAmountKrw = request.sendAmountKrw().setScale(8, RoundingMode.DOWN);
        BigDecimal receiveAmountUsd = sendAmountKrw.divide(exchangeRate, 8, RoundingMode.DOWN);
        BigDecimal percentFee = sendAmountKrw.multiply(PERCENT_FEE_RATE).setScale(8, RoundingMode.DOWN);
        BigDecimal totalFee = FIXED_FEE_KRW.add(percentFee).setScale(8, RoundingMode.DOWN);

        // 견적의 USD 환산 금액으로 건당/연간 해외송금 한도를 검증한다.
        remittanceValidator.validateLimits(userId, receiveAmountUsd);

        String quoteId = UUID.randomUUID().toString();
        LocalDateTime expiredAt = LocalDateTime.now().plusMinutes(QUOTE_EXPIRATION_MINUTES);

        RemittanceQuoteCache cache = new RemittanceQuoteCache(
                userId,
                recipient.getId(),
                KRW,
                sendAmountKrw,
                USD,
                receiveAmountUsd,
                exchangeRate,
                totalFee,
                sendAmountKrw,
                receiveAmountUsd
        );

        redisTemplate.opsForValue().set(
                createQuoteKey(quoteId),
                cache,
                Duration.ofMinutes(QUOTE_EXPIRATION_MINUTES)
        );

        return RemittanceTransactionQuoteResponse.of(
                sendAmountKrw,
                receiveAmountUsd,
                exchangeRate,
                FIXED_FEE_KRW,
                percentFee,
                totalFee,
                quoteId,
                expiredAt
        );
    }

    /**
     * 해외송금 견적 Redis key를 생성한다.
     */
    private String createQuoteKey(String quoteId) {
        return REMITTANCE_QUOTE_KEY_PREFIX + quoteId;
    }
}
