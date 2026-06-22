package com.fxflow.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 해외송금 후속 작업 전용 비동기 설정
 * @Async("remittanceTaskExecutor")가 붙은 메서드만 이 설정을 사용
 * 작업이 너무 많이 몰리면 버리지 않고 호출한 스레드가 직접 처리
 */
@Configuration
public class AsyncConfig {

    @Bean(name = "remittanceTaskExecutor")
    public Executor remittanceTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("remittance-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    // Discord 알림 전용 — 부가 기능이므로 큐 포화 시 드랍 허용
    @Bean(name = "discordAlertExecutor")
    public Executor discordAlertExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("discord-alert-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        executor.initialize();
        return executor;
    }
}
