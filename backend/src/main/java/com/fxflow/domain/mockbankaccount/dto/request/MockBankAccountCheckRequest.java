package com.fxflow.domain.mockbankaccount.dto.request;

import jakarta.validation.constraints.NotBlank;

public record MockBankAccountCheckRequest(
        @NotBlank(message = "계좌번호를 입력해주세요")
        String accountNumber
) {
}
