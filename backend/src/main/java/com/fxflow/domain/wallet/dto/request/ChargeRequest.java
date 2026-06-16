package com.fxflow.domain.wallet.dto.request;

import java.math.BigDecimal;

public record ChargeRequest (
        Long bankAccountId,
        BigDecimal amount
) {
}
