package com.fxflow.domain.companypool.service;

import java.math.BigDecimal;

public interface AdminAlertService {

    void sendBothBelowFloorAlert(BigDecimal krwBalance, BigDecimal usdBalance);

    // 리밸런싱 후에도 매입 풀이 floor 미달 (상대 풀 여유분 부족으로 완전 복구 불가)
    void sendStillBelowFloorAfterRebalancing(String currencyCode, BigDecimal balanceAfter, BigDecimal floorBalance);
}