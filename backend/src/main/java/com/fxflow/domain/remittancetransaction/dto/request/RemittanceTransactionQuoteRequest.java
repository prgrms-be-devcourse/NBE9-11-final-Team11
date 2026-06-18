package com.fxflow.domain.remittancetransaction.dto.request;

import com.fxflow.domain.remittancetransaction.enums.RemittanceReason;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record RemittanceTransactionQuoteRequest(

        @NotNull(message = "수취인 ID는 필수입니다.")
        Long recipientId,

        @NotNull(message = "송금 원화 금액은 필수입니다.")
        @DecimalMin(value = "10000", message = "송금 원화 금액은 10,000원 이상이어야 합니다.")
        BigDecimal sendAmountKrw,

        @NotNull(message = "송금 사유는 필수입니다.")
        RemittanceReason reason
) {
}
