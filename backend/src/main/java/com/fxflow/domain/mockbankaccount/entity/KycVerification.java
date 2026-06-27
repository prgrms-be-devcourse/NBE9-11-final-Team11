package com.fxflow.domain.mockbankaccount.entity;

import com.fxflow.domain.mockbankaccount.enums.KycVerificationStatus;
import com.fxflow.domain.mockbankaccount.errorcode.MockBankAccountErrorCode;
import com.fxflow.domain.user.entity.User;
import com.fxflow.global.entity.BaseEntity;
import com.fxflow.global.exception.BusinessException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 1원 인증(KYC) 세션 — 사용자가 등록한 계좌에 무작위 코드가 포함된 입금자명으로
 * 1원을 입금했다고 가정하고, 사용자가 해당 코드를 직접 조회해 입력하면 검증이 완료된다.
 */
@Entity
@Table(name = "kyc_verifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class KycVerification extends BaseEntity {

    private static final int MAX_ATTEMPTS = 5;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "bank_name", nullable = false, length = 100)
    private String bankName;

    @Column(name = "account_number", nullable = false, length = 50)
    private String accountNumber;

    @Column(name = "account_holder_name", nullable = false, length = 100)
    private String accountHolderName;

    @Column(name = "code", nullable = false, length = 4)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private KycVerificationStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    public static KycVerification create(
            User user,
            String bankName,
            String accountNumber,
            String accountHolderName,
            String code,
            LocalDateTime expiresAt
    ) {
        KycVerification verification = new KycVerification();
        verification.user = user;
        verification.bankName = bankName;
        verification.accountNumber = accountNumber;
        verification.accountHolderName = accountHolderName;
        verification.code = code;
        verification.status = KycVerificationStatus.PENDING;
        verification.attemptCount = 0;
        verification.expiresAt = expiresAt;
        return verification;
    }

    /**
     * '다시 요청'으로 새 코드가 발급될 때 이전 대기 건을 무효화한다.
     */
    public void expire() {
        if (this.status == KycVerificationStatus.PENDING) {
            this.status = KycVerificationStatus.EXPIRED;
        }
    }

    public String depositorName() {
        return "fxflow" + code;
    }

    public boolean isExpired(LocalDateTime now) {
        return status == KycVerificationStatus.EXPIRED || now.isAfter(expiresAt);
    }

    /**
     * 입력된 코드를 검증한다. 만료/시도 초과/불일치 시 예외를 던지고,
     * 일치하면 상태를 VERIFIED로 변경한다.
     */
    public void verify(String inputCode, LocalDateTime now) {
        if (status == KycVerificationStatus.VERIFIED) {
            throw new BusinessException(MockBankAccountErrorCode.KYC_ALREADY_VERIFIED);
        }
        if (isExpired(now)) {
            this.status = KycVerificationStatus.EXPIRED;
            throw new BusinessException(MockBankAccountErrorCode.KYC_CODE_EXPIRED);
        }
        if (attemptCount >= MAX_ATTEMPTS) {
            throw new BusinessException(MockBankAccountErrorCode.KYC_ATTEMPTS_EXCEEDED);
        }

        this.attemptCount++;

        if (!this.code.equals(inputCode)) {
            int remaining = MAX_ATTEMPTS - attemptCount;
            throw new BusinessException(
                    MockBankAccountErrorCode.KYC_CODE_MISMATCH,
                    "인증코드가 일치하지 않습니다. (남은 시도 %d회)".formatted(remaining)
            );
        }

        this.status = KycVerificationStatus.VERIFIED;
    }
}
