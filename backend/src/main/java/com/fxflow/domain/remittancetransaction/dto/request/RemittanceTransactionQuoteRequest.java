package com.fxflow.domain.remittancetransaction.dto.request;

import com.fxflow.domain.remittancetransaction.enums.RemittanceReason;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record RemittanceTransactionQuoteRequest(

        @NotNull(message = "수취인 ID는 필수입니다.")
        Long recipientId,

        @DecimalMin(value = "10000", message = "송금 원화 금액은 10,000원 이상이어야 합니다.")
        BigDecimal sendAmountKrw,

        @DecimalMin(value = "0.01", message = "수취 외화 금액은 0.01 이상이어야 합니다.")
        BigDecimal receiveAmountUsd,

        @NotNull(message = "송금 사유는 필수입니다.")
        RemittanceReason reason
) {
        @AssertTrue(message = "송금 원화 금액 또는 수취 외화 금액 중 하나만 입력해야 합니다.")
        public boolean isSingleAmountPresent() {
                boolean hasSendAmount = sendAmountKrw != null;
                boolean hasReceiveAmount = receiveAmountUsd != null;
                return hasSendAmount != hasReceiveAmount;
        }
}
