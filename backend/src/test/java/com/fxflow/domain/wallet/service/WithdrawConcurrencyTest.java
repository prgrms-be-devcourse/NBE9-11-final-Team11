package com.fxflow.domain.wallet.service;

import com.fxflow.domain.companypool.service.CompanyPoolService;
import com.fxflow.domain.mockbankaccount.service.MockBankAccountService;
import com.fxflow.domain.transactionlimit.entity.TransactionLimit;
import com.fxflow.domain.transactionlimit.enums.LimitTier;
import com.fxflow.domain.transactionlimit.enums.LimitType;
import com.fxflow.domain.transactionlimit.repository.TransactionLimitRepository;
import com.fxflow.domain.user.entity.User;
import com.fxflow.domain.user.repository.UserRepository;
import com.fxflow.domain.userlimitusage.service.UserDailyUsageService;
import com.fxflow.domain.wallet.dto.request.WithdrawRequest;
import com.fxflow.domain.wallet.entity.Wallet;
import com.fxflow.domain.wallet.repository.WalletRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = "spring.sql.init.mode=never")
@ActiveProfiles("h2")
class WithdrawConcurrencyTest {

    @Autowired WalletService walletService;
    @Autowired WalletRepository walletRepository;
    @Autowired UserRepository userRepository;
    @Autowired TransactionLimitRepository transactionLimitRepository;

    @MockitoBean
    MockBankAccountService mockBankAccountService;  // 추가
    @MockitoBean
    CompanyPoolService companyPoolService;          // 추가
    @MockitoBean
    UserDailyUsageService userDailyUsageService;

    private User testUser;
    private Wallet testWallet;


    @BeforeEach
    void setUp() {
        transactionLimitRepository.saveAll(List.of(
                TransactionLimit.create(LimitType.PER_WITHDRAWAL, LimitTier.STANDARD, "KRW", new BigDecimal("2000000")),
                TransactionLimit.create(LimitType.DAILY_WITHDRAWAL, LimitTier.STANDARD, "KRW", new BigDecimal("2000000")),
                TransactionLimit.create(LimitType.PER_WITHDRAWAL, LimitTier.ENHANCED, "KRW", new BigDecimal("3000000")),
                TransactionLimit.create(LimitType.DAILY_WITHDRAWAL, LimitTier.ENHANCED, "KRW", new BigDecimal("3000000"))
        ));

        testUser = userRepository.save(User.create("email", "password", "name"));
        testWallet = walletRepository.save(Wallet.create(testUser, "KRW", new BigDecimal("100000")));
    }

    @AfterEach
    void tearDown() {
        transactionLimitRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();
    }

    private void runConcurrent(int threads, Runnable task) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await(); // 모든 스레드 동시 출발
                    task.run();
                } catch (Exception ignored) {
                } finally {
                    done.countDown();
                }
            });
        }
        ready.await();
        start.countDown();
        done.await();
        executor.shutdown();
    }

    // 락 없음 - 잔액 마이너스 재현
    @Test
    @DisplayName("재시도 없는 낙관적 락 - 충돌 시 예외 발생, 성공건만 정합성 보장")
    void 락없음_재시도없음_충돌시예외발생() throws InterruptedException {
        WithdrawRequest request = new WithdrawRequest(new BigDecimal("9000"));
        AtomicInteger successCount = new AtomicInteger();
        List<String> errors = new CopyOnWriteArrayList<>();

        runConcurrent(5, () -> {
            try {
                walletService.withdraw(testUser.getId(), request);
                successCount.incrementAndGet();
            } catch (Exception e) {
                errors.add(e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        });

        Wallet result = walletRepository.findById(testWallet.getId()).orElseThrow();
        BigDecimal expectedBalance = new BigDecimal("100000")
                .subtract(new BigDecimal("9000").multiply(new BigDecimal(successCount.get())));

        System.out.println("====== 재시도 없는 낙관적 락 결과 ======");
        System.out.println("성공 건수: " + successCount.get());
        System.out.println("실패 건수: " + errors.size());
        System.out.println("실패 원인: " + errors);
        System.out.println("기대 잔액: " + expectedBalance);
        System.out.println("실제 잔액: " + result.getBalance());
        System.out.println("정합성: " + (result.getBalance().compareTo(expectedBalance) == 0 ? "✅ 정상" : "❌ 깨짐"));
        System.out.println("========================================");

        // 충돌로 인해 5건 모두 성공하지 못함을 증명
        assertThat(successCount.get()).isLessThan(5);
        // 성공한 것들은 정합성 보장됨
        assertThat(result.getBalance()).isEqualByComparingTo(expectedBalance);
    }

    // 비관적 락
    @Test
    void 비관적락_동시출금_정합성보장() throws InterruptedException {
        WithdrawRequest request = new WithdrawRequest(new BigDecimal("9000"));
        AtomicInteger successCount = new AtomicInteger();

        runConcurrent(5, () -> {
            try {
                walletService.withdrawWithPessimisticLock(testUser.getId(), request);
                successCount.incrementAndGet();
            } catch (Exception ignored) {}
        });

        Wallet result = walletRepository.findById(testWallet.getId()).orElseThrow();
        System.out.println("비관적락 성공: " + successCount.get() + "건, 최종 잔액: " + result.getBalance());
        assertThat(successCount.get()).isEqualTo(5);
        assertThat(result.getBalance()).isEqualByComparingTo("55000");
    }

    // 낙관적 락
    @Test
    void 낙관적락_동시출금_정합성보장() throws InterruptedException {
        WithdrawRequest request = new WithdrawRequest(new BigDecimal("9000"));
        AtomicInteger successCount = new AtomicInteger();
        List<String> errors = new CopyOnWriteArrayList<>();

        runConcurrent(5, () -> {
            try {
                walletService.withdrawWithOptimisticLock(testUser.getId(), request);
                successCount.incrementAndGet();
            } catch (Exception e) {
                errors.add(e.getClass().getName() + ": " + e.getMessage());
            }
        });

        errors.forEach(System.out::println);

        Wallet result = walletRepository.findById(testWallet.getId()).orElseThrow();
        System.out.println("낙관적락 성공: " + successCount.get() + "건, 최종 잔액: " + result.getBalance());
        // 검증 가능한 것
        assertThat(result.getBalance()).isGreaterThanOrEqualTo(BigDecimal.ZERO);  // 잔액 마이너스 없음
        assertThat(successCount.get()).isGreaterThan(0);  // 최소 1건은 성공
        // 성공 건수 * 9000 + 최종잔액 = 초기잔액 (정합성)
        BigDecimal expectedBalance = new BigDecimal("100000")
                .subtract(new BigDecimal("9000").multiply(new BigDecimal(successCount.get())));
        assertThat(result.getBalance()).isEqualByComparingTo(expectedBalance);
    }

    // atomic 쿼리
    @Test
    void atomic쿼리_동시출금_정합성보장() throws InterruptedException {
        WithdrawRequest request = new WithdrawRequest(new BigDecimal("9000"));
        AtomicInteger successCount = new AtomicInteger();

        runConcurrent(5, () -> {
            try {
                walletService.withdrawWithAtomicQuery(testUser.getId(), request);
                successCount.incrementAndGet();
            } catch (Exception ignored) {}
        });

        Wallet result = walletRepository.findById(testWallet.getId()).orElseThrow();
        System.out.println("atomic 성공: " + successCount.get() + "건, 최종 잔액: " + result.getBalance());
        assertThat(successCount.get()).isEqualTo(5);
        assertThat(result.getBalance()).isEqualByComparingTo("55000");
    }
}
