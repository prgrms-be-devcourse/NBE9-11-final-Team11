package com.fxflow.domain.mockbankaccount.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record KycVerifyRequest(
        @NotNull(message = "인증 요청 ID가 필요합니다.")
        Long verificationId,

        @NotBlank(message = "인증코드를 입력해주세요.")
        String code
) {}
