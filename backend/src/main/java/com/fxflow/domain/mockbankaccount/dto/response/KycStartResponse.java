package com.fxflow.domain.mockbankaccount.dto.response;

import com.fxflow.domain.mockbankaccount.entity.KycVerification;

import java.time.LocalDateTime;

public record KycStartResponse(
        Long verificationId,
        LocalDateTime expiresAt,
        int remainingDailyRequests
) {
    public static KycStartResponse of(KycVerification verification, int remainingDailyRequests) {
        return new KycStartResponse(verification.getId(), verification.getExpiresAt(), remainingDailyRequests);
    }
}
