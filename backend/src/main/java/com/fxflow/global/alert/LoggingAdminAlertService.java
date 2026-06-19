package com.fxflow.global.alert;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 임시 알림 구현체 — 로그만 출력.
 * TODO: DiscordAdminAlertService 구현 후 이 빈을 교체할 것.
 */
@Slf4j
@Service
public class LoggingAdminAlertService implements AdminAlertService {

    @Override
    public void sendBothBelowFloorAlert(BigDecimal krwBalance, BigDecimal usdBalance) {
        log.error("[ALERT] 양 통화 모두 floor 미만 — 즉시 점검 필요. krwBalance={}, usdBalance={}",
                krwBalance, usdBalance);
    }
}