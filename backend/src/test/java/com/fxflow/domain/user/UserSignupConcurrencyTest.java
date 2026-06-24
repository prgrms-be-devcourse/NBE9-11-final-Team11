package com.fxflow.domain.user;

import com.fxflow.domain.user.dto.request.SignupRequest;
import com.fxflow.domain.user.repository.UserRepository;
import com.fxflow.domain.user.service.UserService;
import com.fxflow.global.exception.BusinessException;
import com.fxflow.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 동일 이메일로 동시에 회원가입을 시도했을 때
 * existsByEmail() 체크와 save() 사이의 TOCTOU 구간에서
 * DataIntegrityViolationException(처리 안 된 500)이 새는지 확인한다.
 */
class UserSignupConcurrencyTest extends AbstractIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("동시에 같은 이메일로 가입하면 한 건만 성공하고 나머지는 BusinessException(EMAIL_DUPLICATED)이어야 한다")
    void concurrentSignupWithSameEmail() throws InterruptedException {
        // given
        String email = "race@example.com";
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger businessExceptionCount = new AtomicInteger();
        AtomicInteger unexpectedExceptionCount = new AtomicInteger();
        List<Throwable> unexpected = new java.util.concurrent.CopyOnWriteArrayList<>();

        // when : threadCount개의 스레드가 동시에 같은 이메일로 가입을 시도
        List<Future<?>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    userService.signup(new SignupRequest("홍길동", email, "Abcd1234!"));
                    successCount.incrementAndGet();
                } catch (BusinessException e) {
                    businessExceptionCount.incrementAndGet();
                } catch (Throwable t) {
                    // 여기로 떨어지면 DataIntegrityViolationException 등이
                    // BusinessException으로 변환되지 않고 새는 것 → 버그 재현
                    unexpectedExceptionCount.incrementAndGet();
                    unexpected.add(t);
                }
            }));
        }

        readyLatch.await(5, TimeUnit.SECONDS); // 모든 스레드가 동시에 출발 준비
        startLatch.countDown();                // 동시에 출발

        for (Future<?> f : futures) {
            try {
                f.get(10, TimeUnit.SECONDS);
            } catch (Exception ignored) {
            }
        }
        executor.shutdown();

        // then
        System.out.println("success=" + successCount.get()
                + " businessException=" + businessExceptionCount.get()
                + " unexpected=" + unexpectedExceptionCount.get());
        unexpected.forEach(t -> t.printStackTrace());

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(userRepository.findByEmail(email)).isPresent();

        // 이 assertion이 실패한다면(unexpected > 0) TOCTOU race condition이 재현된 것
        assertThat(unexpectedExceptionCount.get()).isZero();
        assertThat(businessExceptionCount.get()).isEqualTo(threadCount - 1);
    }
}
