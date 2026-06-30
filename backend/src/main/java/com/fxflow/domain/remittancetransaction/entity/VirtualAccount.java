package com.fxflow.domain.remittancetransaction.entity;

import com.fxflow.domain.remittancetransaction.enums.VirtualAccountStatus;
import com.fxflow.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "virtual_accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VirtualAccount extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "remittance_transaction_id", nullable = false)
    private Long remittanceTransactionId;

    @Column(name = "bank_name", length = 100, nullable = false)
    private String bankName;

    @Column(name = "account_number", length = 50, nullable = false, unique = true)
    private String accountNumber;

    @Column(name = "expected_amount", precision = 18, scale = 8, nullable = false)
    private BigDecimal expectedAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private VirtualAccountStatus status;

    @Column(name = "ref_type", length = 50, nullable = false)
    private String refType;

    @Column(name = "ref_id", length = 50, nullable = false)
    private String refId;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "expired_at", nullable = false)
    private LocalDateTime expiredAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    private VirtualAccount(
            Long userId,
            Long remittanceTransactionId,
            String bankName,
            String accountNumber,
            BigDecimal expectedAmount,
            String refType,
            String refId,
            LocalDateTime issuedAt,
            LocalDateTime expiredAt
    ) {
        this.userId = userId;
        this.remittanceTransactionId = remittanceTransactionId;
        this.bankName = bankName;
        this.accountNumber = accountNumber;
        this.expectedAmount = expectedAmount;
        this.status = VirtualAccountStatus.ISSUED;
        this.refType = refType;
        this.refId = refId;
        this.issuedAt = issuedAt;
        this.expiredAt = expiredAt;
    }

    public static VirtualAccount create(
            Long userId,
            Long remittanceTransactionId,
            String bankName,
            String accountNumber,
            BigDecimal expectedAmount,
            String refType,
            String refId,
            LocalDateTime issuedAt,
            LocalDateTime expiredAt
    ) {
        return new VirtualAccount(
                userId,
                remittanceTransactionId,
                bankName,
                accountNumber,
                expectedAmount,
                refType,
                refId,
                issuedAt,
                expiredAt
        );
    }

    // 송금자 가상계좌에 입금할 때, 호출
    public void pay(LocalDateTime paidAt) {
        this.status = VirtualAccountStatus.PAID;
        this.paidAt = paidAt;
    }

    // 입금 기한 지났을 때 호출
    public void expire() {
        this.status = VirtualAccountStatus.EXPIRED;
    }

    // 송금 주문 취소 시 호출
    public void cancel() {
        this.status = VirtualAccountStatus.CANCELED;
    }
}
