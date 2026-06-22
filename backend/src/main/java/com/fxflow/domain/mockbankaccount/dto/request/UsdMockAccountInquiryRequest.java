package com.fxflow.domain.mockbankaccount.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UsdMockAccountInquiryRequest(
        @NotBlank(message = "은행명을 입력해주세요.")
        String bankName,

        @NotBlank(message = "계좌번호를 입력해주세요.")
        String accountNumber,

        @NotBlank(message = "이름을 입력해주세요.")
        String name
) {}
