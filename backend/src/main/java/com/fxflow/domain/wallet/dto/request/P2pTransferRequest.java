package com.fxflow.domain.wallet.dto.request;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record P2pTransferRequest (
        @NotBlank(message = "수취인 이메일은 필수입니다.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        String recipientEmail,

        @NotBlank(message = "통화는 필수입니다.")
        String currency,

        @NotNull(message = "금액은 필수입니다.")
        @DecimalMin(value = "0.01", message = "금액은 0보다 커야 합니다.")
        BigDecimal amount,

        @Size(max = 255, message = "메모는 255자 이하입니다.")
        String memo
){
}
