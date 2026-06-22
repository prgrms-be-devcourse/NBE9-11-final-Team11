package com.fxflow.domain.companypool.service;

import java.math.BigDecimal;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LoggingAdminAlertService implements AdminAlertService {

    @Override
    public void sendBothBelowFloorAlert(BigDecimal krwBalance, BigDecimal usdBalance) {
        log.error("[ALERT] 양 통화 모두 floor 미만 — 즉시 점검 필요. krwBalance={}, usdBalance={}",
                krwBalance, usdBalance);
    }
}