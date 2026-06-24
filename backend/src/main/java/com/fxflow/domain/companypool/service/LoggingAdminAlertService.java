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

    @Override
    public void sendStillBelowFloorAfterRebalancing(String currencyCode, BigDecimal balanceAfter, BigDecimal floorBalance) {
        log.error("[ALERT] 리밸런싱 후에도 floor 미달 — 추가 조치 필요. currency={}, balanceAfter={}, floor={}",
                currencyCode, balanceAfter, floorBalance);
    }
}