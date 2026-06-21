package com.fxflow.global.fx;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 이벤트 파이프라인 검증 (의존성 0, 운영충실).
 * FxRateUpdatedEvent가 트랜잭션 커밋 후에만 @TransactionalEventListener(AFTER_COMMIT)로 전달되고,
 * 롤백 시에는 전달되지 않는지를 무자원 트랜잭션 매니저로 확인한다.
 * (DB·Redis 등 외부 인프라 없이 AFTER_COMMIT 의미만 검증 — main 코드에는 아무것도 추가하지 않는다)
 */
@SpringJUnitConfig(FxRateUpdatedEventPipelineTest.TestConfig.class)
class FxRateUpdatedEventPipelineTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private CapturingListener listener;

    @BeforeEach
    void resetListener() {
        // 컨텍스트가 캐싱되어 리스너 빈이 공유되므로 테스트 간 상태를 격리한다.
        listener.received.clear();
    }

    @Test
    @DisplayName("커밋된 트랜잭션 후 AFTER_COMMIT 리스너가 이벤트를 수신한다")
    void receivesEventAfterCommit() {
        FxRateUpdatedEvent event = new FxRateUpdatedEvent(sampleSnapshot());

        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> eventPublisher.publishEvent(event));

        assertThat(listener.received).containsExactly(event);
    }

    @Test
    @DisplayName("롤백된 트랜잭션의 이벤트는 AFTER_COMMIT 리스너에 전달되지 않는다")
    void doesNotReceiveEventOnRollback() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            eventPublisher.publishEvent(new FxRateUpdatedEvent(sampleSnapshot()));
            status.setRollbackOnly();
        });

        assertThat(listener.received).isEmpty();
    }

    private FxRateSnapshot sampleSnapshot() {
        return new FxRateSnapshot("USD", "KRW",
                new BigDecimal("1350"), new BigDecimal("0.01"),
                LocalDateTime.of(2026, 6, 18, 12, 0));
    }

    @Configuration
    @EnableTransactionManagement // @TransactionalEventListener 처리 인프라(TransactionalEventListenerFactory) 등록
    static class TestConfig {

        @Bean
        PlatformTransactionManager transactionManager() {
            return new ResourcelessTransactionManager();
        }

        @Bean
        CapturingListener capturingListener() {
            return new CapturingListener();
        }
    }

    /** 자원(DB) 없이 트랜잭션 동기화 콜백(afterCommit 등)만 구동하는 트랜잭션 매니저. */
    static class ResourcelessTransactionManager extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            // 자원 없음 — 동기화만 사용
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            // no-op (실제 커밋할 자원 없음)
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            // no-op
        }
    }

    /** 검증 전용 AFTER_COMMIT 리스너 (실제 reservation/notification 리스너가 쓸 phase와 동일). */
    static class CapturingListener {
        final List<FxRateUpdatedEvent> received = new CopyOnWriteArrayList<>();

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        public void on(FxRateUpdatedEvent event) {
            received.add(event);
        }
    }
}
