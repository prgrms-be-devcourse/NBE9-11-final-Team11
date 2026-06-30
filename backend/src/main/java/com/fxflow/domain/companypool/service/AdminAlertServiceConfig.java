package com.fxflow.domain.companypool.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class AdminAlertServiceConfig {

    // DiscordAdminAlertService가 등록되지 않은 경우(URL 미설정)에만 활성화
    @Bean
    @ConditionalOnMissingBean(AdminAlertService.class)
    public AdminAlertService loggingAdminAlertService() {
        return new LoggingAdminAlertService();
    }
}