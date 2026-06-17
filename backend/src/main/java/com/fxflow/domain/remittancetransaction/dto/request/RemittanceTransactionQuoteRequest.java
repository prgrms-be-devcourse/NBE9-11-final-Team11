package com.fxflow.domain.remittancetransaction.dto.request;

import com.fxflow.domain.remittancetransaction.enums.RemittanceReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record RemittanceTransactionQuoteRequest(

        @NotNull(message = "수취인 ID는 필수입니다.")
        Long recipientId,

        @NotNull(message = "송금 원화 금액은 필수입니다.")
        @Positive(message = "송금 원화 금액은 0보다 커야 합니다.")
        BigDecimal sendAmountKrw,

        @NotNull(message = "송금 사유는 필수입니다.")
        RemittanceReason reason
) {
}