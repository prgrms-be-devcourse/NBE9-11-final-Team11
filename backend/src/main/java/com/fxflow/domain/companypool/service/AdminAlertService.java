package com.fxflow.domain.companypool.service;

import java.math.BigDecimal;

public interface AdminAlertService {

    // TODO: Discord webhook 연동 구현 (DiscordAdminAlertService)
    void sendBothBelowFloorAlert(BigDecimal krwBalance, BigDecimal usdBalance);
}