package com.fxflow.domain.wallet.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ExchangeRequest(
        @NotBlank(message = "quoteId는 필수입니다.")
        String quoteId
) {
}
