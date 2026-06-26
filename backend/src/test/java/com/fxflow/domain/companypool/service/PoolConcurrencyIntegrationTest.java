package com.fxflow.domain.companypool.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.fxflow.domain.companypool.entity.CompanyPool;
import com.fxflow.domain.companypool.entity.RebalancingOrder;
import com.fxflow.domain.companypool.enums.TriggerType;
import com.fxflow.domain.companypool.repository.CompanyPoolRepository;
import com.fxflow.domain.companypool.repository.RebalancingRepository;
import com.fxflow.domain.fxrate.service.FxRateQueryService;
import com.fxflow.global.fx.FxRateSnapshot;
import com.fxflow.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@DisplayName("풀 동시성 통합 테스트")
class PoolConcurrencyIntegrationTest extends AbstractIntegrationTest {

    @Autowired private CompanyPoolRepository companyPoolRepository;
    @Autowired private RebalancingService rebalancingService;
    @Autowired private RebalancingRepository rebalancingRepository;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockitoBean private FxRateQueryService fxRateQueryService;

    private static final BigDecimal MID_RATE = new BigDecimal("1300");

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE rebalancing_orders CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE company_pools CASCADE");
        given(fxRateQueryService.getLatestRate("USD", "KRW")).willReturn(
                Optional.of(new FxRateSnapshot("USD", "KRW", MID_RATE, new BigDecimal("0.01"), LocalDateTime.now())));
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("TRUNCATE TABLE rebalancing_orders CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE company_pools CASCADE");
    }

    // ── Test 2: decreaseBalance 원자적 UPDATE ────────────────────────────────
    //
    // CompanyPoolRepository.decreaseBalance() 는 아래 쿼리를 실행한다.
    //   UPDATE company_pools SET balance = balance - :amount
    //   WHERE currency_code = :code AND balance >= :amount
    //
    // 10개 스레드가 동시에 1M씩 차감을 시도하되 초기 잔액이 5M이면
    // 최대 5건만 성공할 수 있다. WHERE 조건이 DB 레벨에서 원자적으로 평가되므로
    // "잔액을 읽고 판단 후 차감"하는 read-then-write 패턴과 달리 경쟁 조건이 없다.
    // 결과: 잔액은 절대 음수가 되지 않고, 정합성(성공건수 × 금액 + 최종잔액 = 초기잔액)이 유지된다.
    @Test
    @DisplayName("동시 차감 → WHERE balance >= :amount 보호로 잔액 음수 불가, 정합성 유지")
    void decreaseBalance_concurrent_neverGoesNegative() throws InterruptedException {
        insertPool("KRW", "5000000", "10000000000", "6000000000", "8000000000", "12000000000");

        int threadCount = 10;
        BigDecimal decreaseAmount = new BigDecimal("1000000"); // 1M씩 차감 시도
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        runConcurrent(threadCount, () -> {
            Integer updated = transactionTemplate.execute(status ->
                    companyPoolRepository.decreaseBalance("KRW", decreaseAmount));
            if (updated != null && updated == 1) {
                successCount.incrementAndGet();
            } else {
                failCount.incrementAndGet();
            }
        });

        CompanyPool krw = companyPoolRepository.findByCurrencyCode("KRW").orElseThrow();
        BigDecimal finalBalance = krw.getBalance();

        log.info("성공: {}건, 실패: {}건, 최종 잔액: {}", successCount.get(), failCount.get(), finalBalance);

        // 잔액은 절대 음수가 되지 않는다
        assertThat(finalBalance).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        // 정합성: 성공 건수 × 1M + 최종잔액 = 초기잔액 5M
        assertThat(finalBalance).isEqualByComparingTo(
                new BigDecimal("5000000").subtract(decreaseAmount.multiply(new BigDecimal(successCount.get()))));
        // 초기잔액(5M) ÷ 1M = 최대 5건 성공
        assertThat(successCount.get()).isLessThanOrEqualTo(5);
    }

    // ── Test 3: PESSIMISTIC_WRITE 락 + 동시 사용자 거래 ─────────────────────────
    //
    // 리밸런싱은 두 풀 행에 SELECT FOR UPDATE(PESSIMISTIC_WRITE)를 걸고 잔액을 읽어 계산한다.
    // 이 락이 해제되기 전까지 같은 행의 UPDATE(사용자 거래)는 DB 레벨에서 블로킹된다.
    //
    // 결과적으로 두 작업은 DB가 직렬화하며, 최종 잔액은
    //   초기잔액 + 리밸런싱 매입량 + 사용자 증가분
    // 이 되어야 한다. 두 작업의 실행 순서와 무관하게 이 등식이 성립하면 "누락(lost update)" 없음이 증명된다.
    //
    // KRW=4.5B로 시작: 사용자 입금(+5억) 후에도 5B < floor(6B) → 두 실행 순서 모두 리밸런싱 트리거됨
    // USD surplus (7.5M-5.2M=2.3M) 기준 상한: 2.3M × 1302.6 ≈ 2.996B → 어느 순서든 동일 cap
    //
    // 실행 순서 A (사용자 거래 선행):
    //   KRW 4.5B → +5억 → 5B → 리밸런싱(5B 기준 shortage=3B, cap≈2.996B) → ≈7.996B
    //   공식: 4.5B + 2.996B + 0.5B ≈ 7.996B ✓
    //
    // 실행 순서 B (리밸런싱 선행):
    //   KRW 4.5B → 리밸런싱(4.5B 기준 shortage=3.5B, cap≈2.996B) → ≈7.496B → +5억 → ≈7.996B
    //   공식: 4.5B + 2.996B + 0.5B ≈ 7.996B ✓
    @Test
    @DisplayName("리밸런싱(PESSIMISTIC_WRITE) + 동시 사용자 거래 → 실행 순서 무관하게 누락 없음")
    void rebalancingAndConcurrentUserDeposit_noLostUpdate() throws InterruptedException {
        // KRW: 4.5B (< floor 6B → 리밸런싱 필요, +5억 후에도 5B < 6B), USD: 7.5M (정상)
        insertPool("KRW", "4500000000", "10000000000", "6000000000", "8000000000", "12000000000");
        insertPool("USD",    "7500000",    "6500000",    "3900000",    "5200000",    "7800000");

        BigDecimal initialKrw = new BigDecimal("4500000000");
        BigDecimal userDepositAmount = new BigDecimal("500000000"); // 5억 증가

        CountDownLatch startLatch = new CountDownLatch(2);
        CountDownLatch doneLatch = new CountDownLatch(2);
        AtomicReference<Exception> rebalancingError = new AtomicReference<>();

        // Thread 1: 수동 리밸런싱 (PESSIMISTIC_WRITE 획득 후 KRW 매입)
        Thread rebalancingThread = new Thread(() -> {
            startLatch.countDown();
            try {
                startLatch.await();
                rebalancingService.execute(TriggerType.MANUAL, null);
            } catch (Exception e) {
                rebalancingError.set(e);
            } finally {
                doneLatch.countDown();
            }
        });

        // Thread 2: 사용자 KRW 직접 증가 (increaseBalance = apply()의 풀 갱신과 동일한 원자적 UPDATE)
        Thread userThread = new Thread(() -> {
            startLatch.countDown();
            try {
                startLatch.await();
                transactionTemplate.execute(status ->
                        companyPoolRepository.increaseBalance("KRW", userDepositAmount));
            } catch (Exception e) {
                // increaseBalance는 예외 발생 상황이 없으므로 기록만
                log.error("사용자 거래 실패", e);
            } finally {
                doneLatch.countDown();
            }
        });

        rebalancingThread.start();
        userThread.start();
        doneLatch.await();

        // 리밸런싱이 예외 없이 완료되어야 함
        assertThat(rebalancingError.get()).isNull();

        // 리밸런싱 이력이 정확히 1건 저장되어야 함
        List<RebalancingOrder> orders = rebalancingRepository.findAllByOrderByCreatedAtDesc();
        assertThat(orders).hasSize(1);
        BigDecimal rebalancedBuyAmount = orders.getFirst().getBuyAmount();

        CompanyPool krw = companyPoolRepository.findByCurrencyCode("KRW").orElseThrow();

        log.info("리밸런싱 매입량: {}, 사용자 증가분: {}, 최종 KRW: {}",
                rebalancedBuyAmount, userDepositAmount, krw.getBalance());

        // 핵심 등식 검증: 실행 순서와 무관하게 누락 없이 두 연산이 모두 반영되어야 한다.
        // finalKRW = initialKRW + rebalancedBuyAmount + userDepositAmount
        BigDecimal expected = initialKrw.add(rebalancedBuyAmount).add(userDepositAmount);
        assertThat(krw.getBalance()).isEqualByComparingTo(expected);
    }

    // ── 헬퍼 ────────────────────────────────────────────────────────────────

    private void runConcurrent(int threadCount, Runnable task) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    task.run();
                } catch (Exception e) {
                    log.debug("스레드 실행 중 예외 (정상 시나리오일 수 있음): {}", e.getMessage());
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

    private void insertPool(String currency, String balance, String target,
                             String floor, String safeFloor, String ceiling) {
        jdbcTemplate.update(
                "INSERT INTO company_pools (currency_code, balance, target_balance, floor_balance, safe_floor_balance, ceiling_balance, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW()) "
                        + "ON CONFLICT (currency_code) DO UPDATE SET "
                        + "balance = EXCLUDED.balance, target_balance = EXCLUDED.target_balance, "
                        + "floor_balance = EXCLUDED.floor_balance, safe_floor_balance = EXCLUDED.safe_floor_balance, "
                        + "ceiling_balance = EXCLUDED.ceiling_balance, updated_at = NOW()",
                currency, new BigDecimal(balance), new BigDecimal(target),
                new BigDecimal(floor), new BigDecimal(safeFloor), new BigDecimal(ceiling));
    }
}