package com.fxflow.domain.wallet.policy;

import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@NoArgsConstructor
public class WalletPolicy {
    public static final BigDecimal MAX_KRW_BALANCE = new BigDecimal("2000000");
}
