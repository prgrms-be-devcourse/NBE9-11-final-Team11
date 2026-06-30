package com.fxflow.domain.mockbankaccount.dto.response;

import com.fxflow.domain.mockbankaccount.entity.KycVerification;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record KycInquiryResponse(
        String depositorName,
        BigDecimal amount,
        LocalDateTime depositedAt
) {
    public static KycInquiryResponse of(KycVerification verification) {
        return new KycInquiryResponse(
                verification.depositorName(),
                BigDecimal.ONE,
                verification.getCreatedAt()
        );
    }
}
