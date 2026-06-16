package com.fxflow.domain.remittancetransaction.service;

import com.fxflow.domain.remittancetransaction.dto.request.RemittanceTransactionCreateRequest;
import com.fxflow.domain.remittancetransaction.dto.response.RemittanceLimitResponse;
import com.fxflow.domain.remittancetransaction.dto.response.RemittanceQuoteSnapshot;
import com.fxflow.domain.remittancetransaction.dto.response.RemittanceTransactionCreateResponse;
import com.fxflow.domain.remittancetransaction.entity.RemittanceTransaction;
import com.fxflow.domain.remittancetransaction.entity.VirtualAccount;
import com.fxflow.domain.remittancetransaction.errorcode.RecipientErrorCode;
import com.fxflow.domain.remittancetransaction.repository.RecipientRepository;
import com.fxflow.domain.remittancetransaction.repository.RemittanceTransactionRepository;
import com.fxflow.domain.remittancetransaction.repository.VirtualAccountRepository;
import com.fxflow.domain.remittancetransaction.validator.RemittanceValidator;
import com.fxflow.domain.transactionlimit.entity.TransactionLimit;
import com.fxflow.domain.transactionlimit.enums.LimitTier;
import com.fxflow.domain.transactionlimit.enums.LimitType;
import com.fxflow.domain.transactionlimit.errorcode.TransactionLimitErrorCode;
import com.fxflow.domain.transactionlimit.repository.TransactionLimitRepository;
import com.fxflow.domain.userlimitusage.entity.UserAnnualUsage;
import com.fxflow.domain.userlimitusage.repository.UserAnnualUsageRepository;
import com.fxflow.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RemittanceTransactionService {

    private static final String USD = "USD";
    private static final LimitTier STANDARD_TIER = LimitTier.STANDARD;
    private static final String DEFAULT_VIRTUAL_ACCOUNT_BANK_NAME = "하나은행";
    private static final String REMITTANCE_REF_TYPE = "REMITTANCE";

    private final UserAnnualUsageRepository userAnnualUsageRepository;
    private final TransactionLimitRepository transactionLimitRepository;
    private final RecipientRepository recipientRepository;
    private final RemittanceTransactionRepository remittanceTransactionRepository;
    private final VirtualAccountRepository virtualAccountRepository;
    private final RemittanceQuoteProvider remittanceQuoteProvider;
    private final RemittanceValidator remittanceValidator;

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
                userId,
                maxPerTransactionUsd,
                maxPerYearUsd,
                currentYearTotalUsd
        );
    }

    /**
     * 송금 사유를 검증하고 송금 주문 생성 후 입금용 가상계좌를 발급한다.
     */
    @Transactional
    public RemittanceTransactionCreateResponse createTransfer(
            Long userId,
            RemittanceTransactionCreateRequest request
    ) {
        RemittanceQuoteSnapshot quote = remittanceQuoteProvider.getQuote(request.quoteId());
        validateRecipient(userId, quote.recipientId());

        // 견적의 USD 환산 금액으로 건당/연간 해외송금 한도를 검증한다.
        remittanceValidator.validateLimits(userId, quote.amountUsd());

        // TODO: 현재는 임시 UUID를 저장한다. 추후 Idempotency-Key 헤더 기반 중복 요청 방지로 교체한다.
        String idempotencyKey = UUID.randomUUID().toString();

        RemittanceTransaction remittanceTransaction = RemittanceTransaction.create(
                userId,
                quote.recipientId(),
                null,
                "BANK_TRANSFER",
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
     * 요청 사용자가 선택한 수취인을 사용할 수 있는지 확인한다.
     */
    private void validateRecipient(Long userId, Long recipientId) {
        if (!recipientRepository.existsByIdAndUserIdAndDeletedAtIsNull(recipientId, userId)) {
            throw new BusinessException(RecipientErrorCode.RECIPIENT_NOT_FOUND);
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
     * 테스트용 가상계좌 번호를 생성한다.
     */
    private String generateVirtualAccountNumber() {
        int first = ThreadLocalRandom.current().nextInt(100, 1000);
        int second = ThreadLocalRandom.current().nextInt(100000, 1000000);
        int third = ThreadLocalRandom.current().nextInt(100000, 1000000);

        return first + "-" + second + "-" + third;
    }
}
