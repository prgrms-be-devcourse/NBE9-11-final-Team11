package com.fxflow.domain.mockbankaccount.service;

import com.fxflow.domain.mockbankaccount.entity.KycVerification;
import com.fxflow.domain.mockbankaccount.errorcode.MockBankAccountErrorCode;
import com.fxflow.domain.mockbankaccount.repository.KycVerificationRepository;
import com.fxflow.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 1원 인증 실패 시도 횟수를 기록한다.
 * verifyKyc()의 메인 트랜잭션은 코드 불일치 시 예외로 인해 롤백되므로,
 * 시도 횟수만큼은 REQUIRES_NEW로 별도 커밋해야 영구히 남는다.
 */
@Service
@RequiredArgsConstructor
public class KycAttemptService {

    private final KycVerificationRepository kycVerificationRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int recordFailedAttempt(Long verificationId) {
        KycVerification verification = kycVerificationRepository.findById(verificationId)
                .orElseThrow(() -> new BusinessException(MockBankAccountErrorCode.KYC_VERIFICATION_NOT_FOUND));

        int remaining = verification.incrementAttemptAndGetRemaining();
        kycVerificationRepository.save(verification);
        return remaining;
    }
}
