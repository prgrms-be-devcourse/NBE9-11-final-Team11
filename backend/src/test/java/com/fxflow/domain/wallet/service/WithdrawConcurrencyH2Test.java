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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = "spring.sql.init.mode=never")
@ActiveProfiles("h2")
class WithdrawConcurrencyH2Test {

    @Autowired WalletService walletService;
    @Autowired WalletRepository walletRepository;
    @Autowired UserRepository userRepository;
    @Autowired TransactionLimitRepository transactionLimitRepository;

    @MockitoBean
    MockBankAccountService mockBankAccountService;
    @MockitoBean
    CompanyPoolService companyPoolService;
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
                } catch (Exception e) {
                    System.out.println(e.getClass());
                    e.printStackTrace();
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

    @Test
    void 비관적락_2개동시출금_정합성보장() throws InterruptedException {
        WithdrawRequest request = new WithdrawRequest(new BigDecimal("9000"));
        AtomicInteger successCount = new AtomicInteger();

        runConcurrent(2, () -> {
            try {
                walletService.withdrawWithPessimisticLockWithDelay(testUser.getId(), request);
                successCount.incrementAndGet();
            } catch (Exception e) {
                System.out.println(e.getClass());
                e.printStackTrace();
            }
        });

        Wallet result = walletRepository.findById(testWallet.getId()).orElseThrow();

        System.out.println("비관적락 성공: " + successCount.get() + "건, 최종 잔액: " + result.getBalance());

        assertThat(successCount.get()).isEqualTo(2);
        assertThat(result.getBalance())
                .isEqualByComparingTo("82000");
    }
}
