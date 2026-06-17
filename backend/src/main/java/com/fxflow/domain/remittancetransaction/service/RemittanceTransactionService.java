package com.fxflow.domain.remittancetransaction.service;

import com.fxflow.domain.companypool.service.CompanyPoolService;
import com.fxflow.domain.mockbankaccount.service.MockBankAccountService;
import com.fxflow.domain.remittancetransaction.dto.request.RemittanceTransactionCreateRequest;
import com.fxflow.domain.remittancetransaction.dto.response.RemittanceLimitResponse;
import com.fxflow.domain.remittancetransaction.dto.response.RemittanceMockFundedResponse;
import com.fxflow.domain.remittancetransaction.dto.response.RemittanceQuoteSnapshot;
import com.fxflow.domain.remittancetransaction.dto.response.RemittanceTransactionCreateResponse;
import com.fxflow.domain.remittancetransaction.entity.Recipient;
import com.fxflow.domain.remittancetransaction.entity.RemittanceTransaction;
import com.fxflow.domain.remittancetransaction.entity.VirtualAccount;
import com.fxflow.domain.remittancetransaction.enums.TransferStatus;
import com.fxflow.domain.remittancetransaction.enums.VirtualAccountStatus;
import com.fxflow.domain.remittancetransaction.errorcode.RecipientErrorCode;
import com.fxflow.domain.remittancetransaction.errorcode.RemittanceTransactionErrorCode;
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

    private static final String KRW = "KRW";
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
    private final MockBankAccountService mockBankAccountService;
    private final CompanyPoolService companyPoolService;

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
        Recipient recipient = getRecipient(userId, quote.recipientId());

        // 견적의 USD 환산 금액으로 건당/연간 해외송금 한도를 검증한다.
        remittanceValidator.validateLimits(userId, quote.amountUsd());

        // TODO: 현재는 임시 UUID를 저장한다. 추후 Idempotency-Key 헤더 기반 중복 요청 방지로 교체한다.
        String idempotencyKey = UUID.randomUUID().toString();

        RemittanceTransaction remittanceTransaction = RemittanceTransaction.create(
                userId,
                quote.recipientId(),
                recipient.getName(),
                recipient.getCountryCode(),
                recipient.getCurrencyCode(),
                recipient.getBankName(),
                recipient.getAccountNumber(),
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

        companyPoolService.deposit(journalId, KRW, krwAmount);

        remittanceTransaction.fund(sourceMockAccountId);
        virtualAccount.pay(paidAt);

        return RemittanceMockFundedResponse.of(remittanceTransaction, virtualAccount);
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
     * 송금 주문 생성 시 사용할 수취인을 조회한다.
     * 조회한 수취인 정보는 송금 당시 정보 보존을 위해 거래 스냅샷으로 저장한다.
     */
    private Recipient getRecipient(Long userId, Long recipientId) {
        return recipientRepository.findByIdAndUserIdAndDeletedAtIsNull(recipientId, userId)
                .orElseThrow(() -> new BusinessException(RecipientErrorCode.RECIPIENT_NOT_FOUND));
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
}