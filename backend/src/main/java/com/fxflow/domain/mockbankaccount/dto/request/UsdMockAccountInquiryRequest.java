// backend/src/main/java/com/fxflow/domain/mockbankaccount/dto/request/UsdMockAccountInquiryRequest.java
package com.fxflow.domain.mockbankaccount.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UsdMockAccountInquiryRequest(
        @NotBlank(message = "은행명을 입력해주세요.")
        String bankName,

        @NotBlank(message = "계좌번호를 입력해주세요.")
        String accountNumber,

        @NotBlank(message = "이름을 입력해주세요.")
        String name,

        @NotBlank(message = "이메일을 입력해주세요.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        String email
) {}