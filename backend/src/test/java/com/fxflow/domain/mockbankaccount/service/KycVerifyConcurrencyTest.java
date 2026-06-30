package com.fxflow.domain.mockbankaccount.service;

import com.fxflow.domain.mockbankaccount.entity.KycVerification;
import com.fxflow.domain.mockbankaccount.enums.KycVerificationStatus;
import com.fxflow.domain.mockbankaccount.exception.KycCodeMismatchException;
import com.fxflow.domain.mockbankaccount.repository.KycVerificationRepository;
import com.fxflow.domain.user.entity.User;
import com.fxflow.domain.user.repository.UserRepository;
import com.fxflow.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * verifyKyc()의 비관적 락(findByIdAndUserId) + noRollbackFor(KycCodeMismatchException) 조합이
 * 실제 DB 트랜잭션에서 동시 요청에 대해 시도 횟수(attemptCount)를 정확히 직렬화하는지 검증한다.
 * Mockito 단위 테스트로는 실제 row 락 동작을 확인할 수 없어 Testcontainers(PostgreSQL)로 검증한다.
 */
@SpringBootTest
@TestPropertySource(properties = "spring.sql.init.mode=never")
class KycVerifyConcurrencyTest extends AbstractIntegrationTest {

    @Autowired
    private MockBankAccountService mockBankAccountService;

    @Autowired
    private KycVerificationRepository kycVerificationRepository;

    @Autowired
    private UserRepository userRepository;

    private static final String BANK_NAME = "국민은행";
    private static final String ACCOUNT_NUMBER = "123456789012";
    private static final String HOLDER_NAME = "홍길동";
    private static final String CORRECT_CODE = "1234";
    private static final String WRONG_CODE = "0000";

    private User testUser;
    private KycVerification verification;

    @BeforeEach
    void setUp() {
        testUser = userRepository.save(User.create("kyc-race@example.com", "password", HOLDER_NAME));
        verification = kycVerificationRepository.save(
                KycVerification.create(
                        testUser, BANK_NAME, ACCOUNT_NUMBER, HOLDER_NAME, CORRECT_CODE,
                        LocalDateTime.now().plusMinutes(5)
                )
        );
    }

    @AfterEach
    void tearDown() {
        kycVerificationRepository.deleteAll();
        if (testUser != null) {
            userRepository.delete(testUser);
        }
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
    void 비관적락_동시에틀린코드제출_시도횟수누락없이정확히기록된다() throws InterruptedException {
        // given: MAX_ATTEMPTS(5)와 동일한 수의 스레드가 동시에 틀린 코드를 제출한다.
        int threads = KycVerification.MAX_ATTEMPTS;
        AtomicInteger mismatchCount = new AtomicInteger();
        List<Integer> remainingValues = new CopyOnWriteArrayList<>();
        List<String> unexpectedErrors = new CopyOnWriteArrayList<>();

        // when
        runConcurrent(threads, () -> {
            try {
                mockBankAccountService.verifyKyc(testUser.getId(), verification.getId(), WRONG_CODE);
            } catch (KycCodeMismatchException e) {
                mismatchCount.incrementAndGet();
                // "(남은 시도 N회)" 형태 메시지에서 남은 횟수를 추출해 중복 값이 없는지도 함께 확인한다.
                String message = e.getMessage();
                int remaining = Integer.parseInt(message.replaceAll(".*시도 (\\d+)회.*", "$1"));
                remainingValues.add(remaining);
            } catch (Exception e) {
                unexpectedErrors.add(e.getClass().getName() + ": " + e.getMessage());
            }
        });

        // then
        unexpectedErrors.forEach(System.out::println);
        assertThat(unexpectedErrors).isEmpty();
        assertThat(mismatchCount.get()).isEqualTo(threads);

        // 락이 없었다면(레이스 컨디션) 여러 스레드가 같은 남은횟수를 읽어 중복이 발생했을 것이다.
        assertThat(remainingValues).hasSize(threads);
        assertThat(remainingValues).doesNotHaveDuplicates();

        KycVerification reloaded = kycVerificationRepository.findById(verification.getId()).orElseThrow();
        assertThat(reloaded.getAttemptCount()).isEqualTo(threads);
        assertThat(reloaded.getStatus()).isEqualTo(KycVerificationStatus.PENDING);
    }
}
