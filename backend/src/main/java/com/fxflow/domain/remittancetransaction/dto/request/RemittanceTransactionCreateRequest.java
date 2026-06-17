package com.fxflow.domain.remittancetransaction.dto.request;

import com.fxflow.domain.remittancetransaction.enums.RemittanceReason;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RemittanceTransactionCreateRequest(

        @NotBlank(message = "송금 견적 ID는 필수입니다.")
        String quoteId,

        @NotNull(message = "송금 사유는 필수입니다.")
        RemittanceReason reason,

        @Size(max = 255, message = "송금 사유 상세는 255자 이하여야 합니다.")
        String reasonDetail
) {
}