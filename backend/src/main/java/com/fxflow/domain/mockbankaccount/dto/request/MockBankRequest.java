package com.fxflow.domain.mockbankaccount.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

public record MockBankRequest(
        @NotBlank(message = "은행명을 입력해주세요")
        String bankName,
        @NotBlank(message = "계좌번호를 입력해주세요")
        String accountNumber

) {
}
