package com.fxflow.domain.wallet.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record WithdrawRequest (
        @NotNull(message = "계좌 ID는 필수입니다.")
        Long bankAccountId,

        @NotNull(message = "충전 금액은 필수입니다.")
        @Positive(message = "충전 금액은 0보다 커야 합니다.")
        BigDecimal amount
) {
}
