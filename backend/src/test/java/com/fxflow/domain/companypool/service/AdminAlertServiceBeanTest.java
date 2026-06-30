package com.fxflow.domain.companypool.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

class AdminAlertServiceBeanTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(AdminAlertServiceConfig.class, DiscordAdminAlertService.class);

    @Test
    @DisplayName("webhook URL 미설정 시 LoggingAdminAlertService가 활성화됨")
    void withoutUrl_loggingServiceIsActivated() {
        contextRunner
                .withPropertyValues("discord.webhook.url=")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(AdminAlertService.class);
                    assertThat(ctx.getBean(AdminAlertService.class))
                            .isInstanceOf(LoggingAdminAlertService.class);
                });
    }

    @Test
    @DisplayName("유효한 webhook URL 설정 시 DiscordAdminAlertService가 활성화됨")
    void withValidUrl_discordServiceIsActivated() {
        RestClient.Builder mockBuilder = mock(RestClient.Builder.class);
        given(mockBuilder.requestFactory(any())).willReturn(mockBuilder);
        given(mockBuilder.build()).willReturn(mock(RestClient.class));

        contextRunner
                .withBean("restClientBuilder", RestClient.Builder.class, () -> mockBuilder)
                .withPropertyValues("discord.webhook.url=https://discord.com/api/webhooks/test")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(AdminAlertService.class);
                    assertThat(ctx.getBean(AdminAlertService.class))
                            .isInstanceOf(DiscordAdminAlertService.class);
                });
    }
}