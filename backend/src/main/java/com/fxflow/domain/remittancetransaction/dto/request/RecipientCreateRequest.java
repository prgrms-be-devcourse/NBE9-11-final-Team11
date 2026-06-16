package com.fxflow.domain.remittancetransaction.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RecipientCreateRequest(

        @NotBlank(message = "수취인 이름은 필수입니다.")
        @Size(max = 100, message = "수취인 이름은 100자 이하여야 합니다.")
        String name,

        @NotBlank(message = "수취 국가 코드는 필수입니다.")
        @Pattern(regexp = "US", message = "MVP에서는 US 국가만 지원합니다.")
        String countryCode,

        @NotBlank(message = "수취 통화 코드는 필수입니다.")
        @Pattern(regexp = "USD", message = "MVP에서는 USD 통화만 지원합니다.")
        String currencyCode,

        @NotBlank(message = "은행명은 필수입니다.")
        @Size(max = 100, message = "은행명은 100자 이하여야 합니다.")
        String bankName,

        @NotBlank(message = "계좌번호는 필수입니다.")
        @Pattern(regexp = "^[0-9]{6,17}$", message = "계좌번호는 숫자 6~17자리여야 합니다.")
        String accountNumber
) {
}