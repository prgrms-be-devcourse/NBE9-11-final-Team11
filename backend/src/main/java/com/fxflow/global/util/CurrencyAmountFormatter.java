package com.fxflow.global.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class CurrencyAmountFormatter {

    private static final String KRW = "KRW";

    private CurrencyAmountFormatter() {
    }

    /**
     * 통화별 표시 단위로 변환
     * - KRW: 소수점 없이 (정수)
     * - 그 외(USD 등): 소수점 2자리
     * 버림(DOWN) 처리 — 화면 표시 금액이 실제 가용 금액보다 커지지 않도록 함
     */
    public static BigDecimal format(BigDecimal amount, String currencyCode) {
        if (KRW.equals(currencyCode)) {
            return amount.setScale(0, RoundingMode.DOWN);
        }
        return amount.setScale(2, RoundingMode.DOWN);
    }
}