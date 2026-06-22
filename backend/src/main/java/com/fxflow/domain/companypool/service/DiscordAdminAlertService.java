package com.fxflow.domain.companypool.service;

import java.math.BigDecimal;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Slf4j
@Primary
@Service
@ConditionalOnProperty(name = "discord.webhook.url")
public class DiscordAdminAlertService implements AdminAlertService {

    private final String webhookUrl;
    private final RestClient restClient;

    public DiscordAdminAlertService(@Value("${discord.webhook.url}") String webhookUrl,
                                    RestClient.Builder restClientBuilder) {
        this.webhookUrl = webhookUrl;
        this.restClient = restClientBuilder.build();
    }

    @Override
    public void sendBothBelowFloorAlert(BigDecimal krwBalance, BigDecimal usdBalance) {
        String message = String.format(
                "⚠️ **[긴급] 양 통화 모두 Floor 미만 — 즉시 수동 점검 필요**\n" +
                "KRW 잔액: %s 원\n" +
                "USD 잔액: %s 달러",
                krwBalance.toPlainString(), usdBalance.toPlainString()
        );
        try {
            restClient.post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("content", message))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Discord 관리자 알림 전송 완료.");
        } catch (Exception e) {
            log.error("Discord 알림 전송 실패. krwBalance={}, usdBalance={}", krwBalance, usdBalance, e);
        }
    }
}
