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
@Table(
        name = "kyc_verifications",
        indexes = {
                // countByUserIdAndCreatedAtAfter(일일 요청 한도 체크)와
                // findAllByUserIdAndStatus(이전 PENDING 만료 처리) 조회 최적화
                @Index(name = "idx_kyc_verifications_user_id_created_at", columnList = "user_id, created_at"),
                @Index(name = "idx_kyc_verifications_user_id_status", columnList = "user_id, status")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class KycVerification extends BaseEntity {

    public static final int MAX_ATTEMPTS = 5;

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
     * 만료/이미인증됨/시도초과 여부를 검사한다. (상태 변경 없는 순수 검사)
     */
    public void assertVerifiable(LocalDateTime now) {
        if (status == KycVerificationStatus.VERIFIED) {
            throw new BusinessException(MockBankAccountErrorCode.KYC_ALREADY_VERIFIED);
        }
        if (isExpired(now)) {
            throw new BusinessException(MockBankAccountErrorCode.KYC_CODE_EXPIRED);
        }
        if (attemptCount >= MAX_ATTEMPTS) {
            throw new BusinessException(MockBankAccountErrorCode.KYC_ATTEMPTS_EXCEEDED);
        }
    }

    public boolean matchesCode(String inputCode) {
        return this.code.equals(inputCode);
    }

    public void markVerified() {
        this.status = KycVerificationStatus.VERIFIED;
    }

    /**
     * 실패한 시도를 1 증가시키고 남은 시도 횟수를 반환한다.
     * MockBankAccountService.verifyKyc()가 findByIdAndUserId의 비관적 쓰기 락으로 동시 요청을 직렬화하고,
     * KycCodeMismatchException을 noRollbackFor로 지정해 이 증가분이 트랜잭션 롤백 없이 커밋되도록 한다.
     */
    public int incrementAttemptAndGetRemaining() {
        this.attemptCount++;
        return MAX_ATTEMPTS - attemptCount;
    }
}
