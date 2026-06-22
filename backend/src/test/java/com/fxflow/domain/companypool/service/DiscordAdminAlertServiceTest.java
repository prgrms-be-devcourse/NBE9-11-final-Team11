package com.fxflow.domain.companypool.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.RequestBodySpec;
import org.springframework.web.client.RestClient.RequestBodyUriSpec;
import org.springframework.web.client.RestClient.ResponseSpec;

@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class DiscordAdminAlertServiceTest {

    @Mock private RestClient restClient;
    @Mock private RestClient.Builder restClientBuilder;
    @Mock private RequestBodyUriSpec requestBodyUriSpec;
    @Mock private RequestBodySpec requestBodySpec;
    @Mock private ResponseSpec responseSpec;

    private DiscordAdminAlertService discordAdminAlertService;

    private static final String WEBHOOK_URL = "https://discord.com/api/webhooks/test";

    @BeforeEach
    void setUp() {
        given(restClientBuilder.build()).willReturn(restClient);
        discordAdminAlertService = new DiscordAdminAlertService(WEBHOOK_URL, restClientBuilder);

        given(restClient.post()).willReturn(requestBodyUriSpec);
        given(requestBodyUriSpec.uri(WEBHOOK_URL)).willReturn(requestBodySpec);
        given(requestBodySpec.contentType(MediaType.APPLICATION_JSON)).willReturn(requestBodySpec);
        given(requestBodySpec.body(any())).willReturn(requestBodySpec);
        given(requestBodySpec.retrieve()).willReturn(responseSpec);
    }

    @Test
    @DisplayName("웹훅 요청 실패 시 예외 전파 없이 로그만 출력")
    void sendBothBelowFloorAlert_webhookFails_noExceptionPropagated() {
        given(responseSpec.toBodilessEntity()).willThrow(new RuntimeException("Discord 연결 실패"));

        assertThatCode(() -> discordAdminAlertService.sendBothBelowFloorAlert(
                new BigDecimal("7000000000"), new BigDecimal("5000000")))
                .doesNotThrowAnyException();
    }
}