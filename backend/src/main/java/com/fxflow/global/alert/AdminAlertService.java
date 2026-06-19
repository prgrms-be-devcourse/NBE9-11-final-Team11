package com.fxflow.global.alert;

import java.math.BigDecimal;

public interface AdminAlertService {

    // TODO: Discord webhook 연동 구현 (DiscordAdminAlertService)
    void sendBothBelowFloorAlert(BigDecimal krwBalance, BigDecimal usdBalance);
}