package com.fxflow.domain.remittancetransaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.fxflow.domain.companypool.entity.CompanyPool;
import com.fxflow.domain.companypool.repository.CompanyPoolRepository;
import com.fxflow.domain.fxrate.service.FxRateQueryService;
import com.fxflow.domain.ledger.entity.LedgerEntry;
import com.fxflow.domain.ledger.enums.LedgerDirection;
import com.fxflow.domain.ledger.enums.LedgerEntryType;
import com.fxflow.domain.ledger.enums.LedgerRefType;
import com.fxflow.domain.ledger.repository.LedgerEntryRepository;
import com.fxflow.domain.mockbankaccount.entity.MockBankAccount;
import com.fxflow.domain.mockbankaccount.repository.MockBankAccountRepository;
import com.fxflow.domain.remittancetransaction.dto.request.RemittanceTransactionCreateRequest;
import com.fxflow.domain.remittancetransaction.dto.request.RemittanceTransactionQuoteRequest;
import com.fxflow.domain.remittancetransaction.dto.response.RemittanceTransactionCreateResponse;
import com.fxflow.domain.remittancetransaction.dto.response.RemittanceTransactionQuoteResponse;
import com.fxflow.domain.remittancetransaction.entity.Recipient;
import com.fxflow.domain.remittancetransaction.entity.RemittanceTransaction;
import com.fxflow.domain.remittancetransaction.entity.VirtualAccount;
import com.fxflow.domain.remittancetransaction.enums.RemittanceReason;
import com.fxflow.domain.remittancetransaction.enums.TransferStatus;
import com.fxflow.domain.remittancetransaction.enums.VirtualAccountStatus;
import com.fxflow.domain.remittancetransaction.event.RemittanceFundedEventListener;
import com.fxflow.domain.remittancetransaction.repository.RecipientRepository;
import com.fxflow.domain.remittancetransaction.repository.RemittanceTransactionRepository;
import com.fxflow.domain.remittancetransaction.repository.VirtualAccountRepository;
import com.fxflow.domain.transactionlimit.entity.TransactionLimit;
import com.fxflow.domain.transactionlimit.enums.LimitTier;
import com.fxflow.domain.transactionlimit.enums.LimitType;
import com.fxflow.domain.transactionlimit.repository.TransactionLimitRepository;
import com.fxflow.domain.user.entity.User;
import com.fxflow.domain.user.repository.UserRepository;
import com.fxflow.domain.userlimitusage.repository.UserAnnualUsageRepository;
import com.fxflow.global.fx.FxRateSnapshot;
import com.fxflow.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DisplayName("해외송금 돈 정합성 및 복식부기 통합 테스트")
class RemittanceFlowIntegrationTest extends AbstractIntegrationTest {

    private static final String KRW = "KRW";
    private static final String USD = "USD";
    private static final BigDecimal SEND_AMOUNT_KRW = new BigDecimal("1000000");
    private static final BigDecimal EXCHANGE_RATE = new BigDecimal("1000.00000000");
    private static final BigDecimal INITIAL_SENDER_KRW = new BigDecimal("10000000.00000000");
    private static final BigDecimal INITIAL_KRW_POOL = new BigDecimal("100000000.00");
    private static final BigDecimal INITIAL_USD_POOL = new BigDecimal("100000.00");
    private static final BigDecimal INSUFFICIENT_USD_POOL = new BigDecimal("100.00");

    @Autowired private RemittanceTransactionService remittanceTransactionService;
    @Autowired private RemittancePayoutService remittancePayoutService;
    @Autowired private RemittanceTransactionRepository remittanceTransactionRepository;
    @Autowired private VirtualAccountRepository virtualAccountRepository;
    @Autowired private RecipientRepository recipientRepository;
    @Autowired private MockBankAccountRepository mockBankAccountRepository;
    @Autowired private CompanyPoolRepository companyPoolRepository;
    @Autowired private TransactionLimitRepository transactionLimitRepository;
    @Autowired private UserAnnualUsageRepository userAnnualUsageRepository;
    @Autowired private LedgerEntryRepository ledgerEntryRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockitoBean private FxRateQueryService exchangeRateProvider;
    @MockitoBean private RemittanceFundedEventListener remittanceFundedEventListener;

    @BeforeEach
    void setUp() {
        truncateAll();
        seedRemittanceLimits();
        given(exchangeRateProvider.getLatestRate(USD, KRW)).willReturn(
                Optional.of(new FxRateSnapshot(
                        USD,
                        KRW,
                        EXCHANGE_RATE,
                        BigDecimal.ZERO,
                        LocalDateTime.now()
                ))
        );
    }

    @AfterEach
    void cleanUp() {
        truncateAll();
    }

    @Test
    @DisplayName("정상 송금: 송금자 KRW 차감, 회사 KRW 풀 증가, 회사 USD 풀 차감, 수취인 USD 입금이 복식부기로 기록된다")
    void completeRemittance_updatesBalancesAndLedgerEntries() {
        insertCompanyPool(KRW, INITIAL_KRW_POOL);
        insertCompanyPool(USD, INITIAL_USD_POOL);

        User sender = createUser("sender-flow@example.com", "송금자");
        createSenderKrwAccount(sender);
        Recipient recipient = createRecipient(sender, "John Doe", "Chase Bank", "98765432101");

        RemittanceTransactionCreateResponse createResponse =
                createTransfer(sender, recipient, "idem-success-001");
        Long transferId = createResponse.transferId();

        VirtualAccount virtualAccount = getVirtualAccount(transferId);
        BigDecimal expectedKrwPayment = virtualAccount.getExpectedAmount();
        BigDecimal expectedUsdPayout = getTransfer(transferId).getReceiveAmount();

        remittanceTransactionService.mockFundTransfer(sender.getId(), transferId);
        remittancePayoutService.processPayout(transferId);

        RemittanceTransaction completed = getTransfer(transferId);
        assertThat(completed.getStatus()).isEqualTo(TransferStatus.COMPLETED);

        assertMoney(senderKrwAccount(sender).getBalance(), INITIAL_SENDER_KRW.subtract(expectedKrwPayment));
        assertMoney(pool(KRW).getBalance(), INITIAL_KRW_POOL.add(expectedKrwPayment));
        assertMoney(pool(USD).getBalance(), INITIAL_USD_POOL.subtract(expectedUsdPayout));
        assertMoney(recipientUsdAccount(recipient).getBalance(), expectedUsdPayout);

        assertMoney(annualUsedUsd(sender), expectedUsdPayout);

        assertCompletedRemittanceLedgerEntries(completed.getJournalId(), expectedKrwPayment, expectedUsdPayout);
    }

    @Test
    @DisplayName("외화풀 부족: USD 지급 실패 시 송금자에게 KRW를 환불하고 회사 KRW 풀과 선점 한도를 복구한다")
    void payoutFailure_refundsKrwAndRestoresAnnualLimit() {
        insertCompanyPool(KRW, INITIAL_KRW_POOL);
        insertCompanyPool(USD, INSUFFICIENT_USD_POOL);

        User sender = createUser("sender-refund@example.com", "환불대상");
        createSenderKrwAccount(sender);
        Recipient recipient = createRecipient(sender, "Jane Doe", "Bank of America", "98765432102");

        RemittanceTransactionCreateResponse createResponse =
                createTransfer(sender, recipient, "idem-refund-001");
        Long transferId = createResponse.transferId();

        BigDecimal expectedKrwPayment = getVirtualAccount(transferId).getExpectedAmount();

        remittanceTransactionService.mockFundTransfer(sender.getId(), transferId);
        remittancePayoutService.processPayout(transferId);

        RemittanceTransaction failed = getTransfer(transferId);
        assertThat(failed.getStatus()).isEqualTo(TransferStatus.FAILED);

        assertMoney(senderKrwAccount(sender).getBalance(), INITIAL_SENDER_KRW);
        assertMoney(pool(KRW).getBalance(), INITIAL_KRW_POOL);
        assertMoney(pool(USD).getBalance(), INSUFFICIENT_USD_POOL);
        assertMoney(recipientUsdAccount(recipient).getBalance(), BigDecimal.ZERO);
        assertMoney(annualUsedUsd(sender), BigDecimal.ZERO);

        assertRefundedRemittanceLedgerEntries(failed.getJournalId(), expectedKrwPayment);
    }

    @Test
    @DisplayName("외화풀 동시성: 동시에 두 건이 마지막 USD 풀을 점유해도 한 건만 지급되고 한 건은 환불된다")
    void concurrentPayout_allowsOnlyOneTransferWhenUsdPoolIsEnoughForOne() throws Exception {
        insertCompanyPool(KRW, INITIAL_KRW_POOL);

        User sender = createUser("sender-concurrent@example.com", "동시성대상");
        createSenderKrwAccount(sender);
        Recipient recipient = createRecipient(sender, "Concurrent Recipient", "Citibank", "98765432104");

        RemittanceTransactionCreateResponse firstCreateResponse =
                createTransfer(sender, recipient, "idem-concurrent-001");
        RemittanceTransactionCreateResponse secondCreateResponse =
                createTransfer(sender, recipient, "idem-concurrent-002");
        Long firstTransferId = firstCreateResponse.transferId();
        Long secondTransferId = secondCreateResponse.transferId();

        BigDecimal expectedKrwPayment = getVirtualAccount(firstTransferId).getExpectedAmount();
        BigDecimal expectedUsdPayout = getTransfer(firstTransferId).getReceiveAmount();
        insertCompanyPool(USD, expectedUsdPayout);

        remittanceTransactionService.mockFundTransfer(sender.getId(), firstTransferId);
        remittanceTransactionService.mockFundTransfer(sender.getId(), secondTransferId);

        runPayoutsConcurrently(firstTransferId, secondTransferId);

        RemittanceTransaction firstTransfer = getTransfer(firstTransferId);
        RemittanceTransaction secondTransfer = getTransfer(secondTransferId);
        List<TransferStatus> statuses = List.of(firstTransfer.getStatus(), secondTransfer.getStatus());

        assertThat(statuses).containsExactlyInAnyOrder(TransferStatus.COMPLETED, TransferStatus.FAILED);
        assertMoney(senderKrwAccount(sender).getBalance(), INITIAL_SENDER_KRW.subtract(expectedKrwPayment));
        assertMoney(pool(KRW).getBalance(), INITIAL_KRW_POOL.add(expectedKrwPayment));
        assertMoney(pool(USD).getBalance(), BigDecimal.ZERO);
        assertMoney(recipientUsdAccount(recipient).getBalance(), expectedUsdPayout);
        assertMoney(annualUsedUsd(sender), expectedUsdPayout);

        RemittanceTransaction completed = statuses.get(0) == TransferStatus.COMPLETED
                ? firstTransfer
                : secondTransfer;
        RemittanceTransaction failed = statuses.get(0) == TransferStatus.FAILED
                ? firstTransfer
                : secondTransfer;

        assertCompletedRemittanceLedgerEntries(completed.getJournalId(), expectedKrwPayment, expectedUsdPayout);
        assertRefundedRemittanceLedgerEntries(failed.getJournalId(), expectedKrwPayment);
    }

    @Test
    @DisplayName("미입금 만료: 가상계좌는 EXPIRED, 송금 거래는 CANCELED가 되고 선점 한도를 복구한다")
    void expirePendingTransfer_cancelsTransferAndRestoresAnnualLimit() {
        insertCompanyPool(KRW, INITIAL_KRW_POOL);
        insertCompanyPool(USD, INITIAL_USD_POOL);

        User sender = createUser("sender-expire@example.com", "만료대상");
        createSenderKrwAccount(sender);
        Recipient recipient = createRecipient(sender, "Expired Recipient", "Wells Fargo", "98765432103");

        RemittanceTransactionCreateResponse createResponse =
                createTransfer(sender, recipient, "idem-expire-001");
        Long transferId = createResponse.transferId();

        assertMoney(annualUsedUsd(sender), getTransfer(transferId).getAmountUsd());

        int expiredCount = remittanceTransactionService.expirePendingTransfers(LocalDateTime.now().plusMinutes(11));

        assertThat(expiredCount).isEqualTo(1);
        assertThat(getTransfer(transferId).getStatus()).isEqualTo(TransferStatus.CANCELED);
        assertThat(getVirtualAccount(transferId).getStatus()).isEqualTo(VirtualAccountStatus.EXPIRED);
        assertMoney(annualUsedUsd(sender), BigDecimal.ZERO);
        assertMoney(senderKrwAccount(sender).getBalance(), INITIAL_SENDER_KRW);
        assertMoney(pool(KRW).getBalance(), INITIAL_KRW_POOL);
        assertMoney(pool(USD).getBalance(), INITIAL_USD_POOL);
        assertThat(ledgerEntryRepository.findAll()).isEmpty();
    }

    private RemittanceTransactionCreateResponse createTransfer(
            User sender,
            Recipient recipient,
            String idempotencyKey
    ) {
        RemittanceTransactionQuoteResponse quoteResponse = remittanceTransactionService.createQuote(
                sender.getId(),
                new RemittanceTransactionQuoteRequest(
                        recipient.getId(),
                        SEND_AMOUNT_KRW,
                        RemittanceReason.FAMILY_SUPPORT
                )
        );

        return remittanceTransactionService.createTransfer(
                sender.getId(),
                new RemittanceTransactionCreateRequest(
                        quoteResponse.quoteId(),
                        RemittanceReason.FAMILY_SUPPORT,
                        "통합 테스트 송금"
                ),
                idempotencyKey
        );
    }

    private User createUser(String email, String name) {
        return userRepository.save(User.create(email, "password", name));
    }

    private Recipient createRecipient(
            User sender,
            String name,
            String bankName,
            String accountNumber
    ) {
        Recipient recipient = recipientRepository.save(Recipient.create(
                sender.getId(),
                name,
                "US",
                USD,
                bankName,
                accountNumber
        ));
        mockBankAccountRepository.save(MockBankAccount.createRecipientAccount(
                recipient.getName(),
                recipient.getCurrencyCode(),
                recipient.getBankName(),
                recipient.getAccountNumber(),
                BigDecimal.ZERO
        ));
        return recipient;
    }

    private MockBankAccount createSenderKrwAccount(User sender) {
        return mockBankAccountRepository.save(
                MockBankAccount.create(sender, "국민은행", "123456789012")
        );
    }

    private MockBankAccount senderKrwAccount(User sender) {
        return mockBankAccountRepository
                .findFirstByUser_IdAndCurrencyCodeAndDeletedAtIsNull(sender.getId(), KRW)
                .orElseThrow();
    }

    private MockBankAccount recipientUsdAccount(Recipient recipient) {
        return mockBankAccountRepository
                .findByAccountNumberAndCurrencyCodeAndDeletedAtIsNull(
                        recipient.getAccountNumber(),
                        recipient.getCurrencyCode()
                )
                .orElseThrow();
    }

    private RemittanceTransaction getTransfer(Long transferId) {
        return remittanceTransactionRepository.findById(transferId).orElseThrow();
    }

    private VirtualAccount getVirtualAccount(Long transferId) {
        return virtualAccountRepository.findByRemittanceTransactionId(transferId).orElseThrow();
    }

    private CompanyPool pool(String currencyCode) {
        return companyPoolRepository.findByCurrencyCode(currencyCode).orElseThrow();
    }

    private BigDecimal annualUsedUsd(User user) {
        int currentYear = LocalDate.now(ZoneId.of("Asia/Seoul")).getYear();
        return userAnnualUsageRepository
                .findByUserIdAndYear(user.getId(), currentYear)
                .orElseThrow()
                .getAnnualUsedUsd();
    }

    private void runPayoutsConcurrently(Long firstTransferId, Long secondTransferId) throws Exception {
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        try {
            Future<?> firstFuture = executorService.submit(() -> processPayoutAfterStartSignal(
                    firstTransferId,
                    readyLatch,
                    startLatch
            ));
            Future<?> secondFuture = executorService.submit(() -> processPayoutAfterStartSignal(
                    secondTransferId,
                    readyLatch,
                    startLatch
            ));

            assertThat(readyLatch.await(3, TimeUnit.SECONDS)).isTrue();
            startLatch.countDown();

            firstFuture.get(10, TimeUnit.SECONDS);
            secondFuture.get(10, TimeUnit.SECONDS);
        } finally {
            executorService.shutdownNow();
        }
    }

    private void processPayoutAfterStartSignal(
            Long transferId,
            CountDownLatch readyLatch,
            CountDownLatch startLatch
    ) {
        readyLatch.countDown();
        try {
            startLatch.await();
            remittancePayoutService.processPayout(transferId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private void assertCompletedRemittanceLedgerEntries(
            String journalId,
            BigDecimal krwAmount,
            BigDecimal usdAmount
    ) {
        List<LedgerEntry> entries = remittanceLedgerEntries(journalId);

        assertThat(entries).hasSize(4);
        assertRemittanceLedgerEntry(entries, KRW, LedgerDirection.DEBIT, krwAmount);
        assertRemittanceLedgerEntry(entries, KRW, LedgerDirection.CREDIT, krwAmount);
        assertRemittanceLedgerEntry(entries, USD, LedgerDirection.DEBIT, usdAmount);
        assertRemittanceLedgerEntry(entries, USD, LedgerDirection.CREDIT, usdAmount);
    }

    private void assertRefundedRemittanceLedgerEntries(String journalId, BigDecimal krwAmount) {
        List<LedgerEntry> entries = remittanceLedgerEntries(journalId);

        assertThat(entries).hasSize(4);
        assertThat(entries)
                .filteredOn(entry -> entry.getCurrencyCode().equals(USD))
                .isEmpty();
        assertThat(entries)
                .filteredOn(entry -> entry.getCurrencyCode().equals(KRW))
                .hasSize(4);
        assertThat(entries)
                .filteredOn(entry -> entry.getCurrencyCode().equals(KRW))
                .filteredOn(entry -> entry.getLedgerDirection() == LedgerDirection.DEBIT)
                .hasSize(2)
                .allSatisfy(entry -> assertThat(entry.getAmount()).isEqualByComparingTo(krwAmount));
        assertThat(entries)
                .filteredOn(entry -> entry.getCurrencyCode().equals(KRW))
                .filteredOn(entry -> entry.getLedgerDirection() == LedgerDirection.CREDIT)
                .hasSize(2)
                .allSatisfy(entry -> assertThat(entry.getAmount()).isEqualByComparingTo(krwAmount));
    }

    private List<LedgerEntry> remittanceLedgerEntries(String journalId) {
        return ledgerEntryRepository.findAll()
                .stream()
                .filter(entry -> entry.getJournalId().equals(journalId))
                .toList();
    }

    private void assertRemittanceLedgerEntry(
            List<LedgerEntry> entries,
            String currencyCode,
            LedgerDirection direction,
            BigDecimal amount
    ) {
        assertThat(entries)
                .filteredOn(entry -> entry.getCurrencyCode().equals(currencyCode))
                .filteredOn(entry -> entry.getLedgerDirection() == direction)
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.getEntryType()).isEqualTo(LedgerEntryType.TRANSFER);
                    assertThat(entry.getAmount()).isEqualByComparingTo(amount);
                    assertThat(entry.getRefType()).isEqualTo(LedgerRefType.REMITTANCE);
                    assertThat(entry.getRefId()).isEqualTo(entry.getJournalId());
                });
    }

    private void seedRemittanceLimits() {
        transactionLimitRepository.save(TransactionLimit.create(
                LimitType.PER_REMITTANCE,
                LimitTier.STANDARD,
                USD,
                new BigDecimal("5000.00000000")
        ));
        transactionLimitRepository.save(TransactionLimit.create(
                LimitType.ANNUAL_REMITTANCE,
                LimitTier.STANDARD,
                USD,
                new BigDecimal("100000.00000000")
        ));
    }

    private void insertCompanyPool(String currencyCode, BigDecimal balance) {
        jdbcTemplate.update(
                """
                        INSERT INTO company_pools
                            (currency_code, balance, target_balance, floor_balance, ceiling_balance, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, now(), now())
                        """,
                currencyCode,
                balance,
                balance,
                BigDecimal.ZERO,
                new BigDecimal("999999999999.00")
        );
    }

    private void assertMoney(BigDecimal actual, BigDecimal expected) {
        assertThat(actual).isEqualByComparingTo(expected);
    }

    private void truncateAll() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    ledger_entries,
                    virtual_accounts,
                    remittance_transactions,
                    mock_bank_accounts,
                    recipients,
                    user_annual_usages,
                    transaction_limits,
                    company_pools,
                    wallets,
                    users
                RESTART IDENTITY CASCADE
                """);
    }
}
