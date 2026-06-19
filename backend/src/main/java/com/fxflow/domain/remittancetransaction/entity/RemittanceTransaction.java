package com.fxflow.domain.remittancetransaction.entity;

import com.fxflow.domain.remittancetransaction.enums.TransferStatus;
import com.fxflow.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "remittance_transactions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RemittanceTransaction extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "recipient_id", nullable = false)
    private Long recipientId;

    @Column(name = "target_user_id")
    private Long targetUserId;

    @Column(name = "method", length = 30, nullable = false)
    private String method;

    @Column(name = "source_wallet_id")
    private Long sourceWalletId;

    @Column(name = "target_wallet_id")
    private Long targetWalletId;

    @Column(name = "source_mock_account_id")
    private Long sourceMockAccountId;

    @Column(name = "target_mock_account_id")
    private Long targetMockAccountId;

    @Column(name = "exchange_transaction_id")
    private Long exchangeTransactionId;

    @Column(name = "send_currency", length = 10, nullable = false)
    private String sendCurrency;

    @Column(name = "send_amount", precision = 18, scale = 8, nullable = false)
    private BigDecimal sendAmount;

    @Column(name = "receive_currency", length = 10, nullable = false)
    private String receiveCurrency;

    @Column(name = "receive_amount", precision = 18, scale = 8, nullable = false)
    private BigDecimal receiveAmount;

    @Column(name = "applied_rate", precision = 18, scale = 8, nullable = false)
    private BigDecimal appliedRate;

    @Column(name = "fee_amount", precision = 18, scale = 8, nullable = false)
    private BigDecimal feeAmount;

    @Column(name = "amount_krw", precision = 18, scale = 8, nullable = false)
    private BigDecimal amountKrw;

    @Column(name = "amount_usd", precision = 18, scale = 8, nullable = false)
    private BigDecimal amountUsd;

    @Column(name = "reason", length = 50, nullable = false)
    private String reason;

    @Column(name = "reason_detail", length = 255)
    private String reasonDetail;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private TransferStatus status;

    @Column(name = "idempotency_key", length = 100, nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    private RemittanceTransaction(
            Long userId,
            Long recipientId,
            Long targetUserId,
            String method,
            Long sourceWalletId,
            Long targetWalletId,
            Long sourceMockAccountId,
            Long targetMockAccountId,
            Long exchangeTransactionId,
            String sendCurrency,
            BigDecimal sendAmount,
            String receiveCurrency,
            BigDecimal receiveAmount,
            BigDecimal appliedRate,
            BigDecimal feeAmount,
            BigDecimal amountKrw,
            BigDecimal amountUsd,
            String reason,
            String reasonDetail,
            String idempotencyKey
    ) {
        this.userId = userId;
        this.recipientId = recipientId;
        this.targetUserId = targetUserId;
        this.method = method;
        this.sourceWalletId = sourceWalletId;
        this.targetWalletId = targetWalletId;
        this.sourceMockAccountId = sourceMockAccountId;
        this.targetMockAccountId = targetMockAccountId;
        this.exchangeTransactionId = exchangeTransactionId;
        this.sendCurrency = sendCurrency;
        this.sendAmount = sendAmount;
        this.receiveCurrency = receiveCurrency;
        this.receiveAmount = receiveAmount;
        this.appliedRate = appliedRate;
        this.feeAmount = feeAmount;
        this.amountKrw = amountKrw;
        this.amountUsd = amountUsd;
        this.reason = reason;
        this.reasonDetail = reasonDetail;
        this.status = TransferStatus.PENDING;
        this.idempotencyKey = idempotencyKey;
    }

    public static RemittanceTransaction create(
            Long userId,
            Long recipientId,
            Long targetUserId,
            String method,
            Long sourceWalletId,
            Long targetWalletId,
            Long sourceMockAccountId,
            Long targetMockAccountId,
            Long exchangeTransactionId,
            String sendCurrency,
            BigDecimal sendAmount,
            String receiveCurrency,
            BigDecimal receiveAmount,
            BigDecimal appliedRate,
            BigDecimal feeAmount,
            BigDecimal amountKrw,
            BigDecimal amountUsd,
            String reason,
            String reasonDetail,
            String idempotencyKey
    ) {
        return new RemittanceTransaction(
                userId,
                recipientId,
                targetUserId,
                method,
                sourceWalletId,
                targetWalletId,
                sourceMockAccountId,
                targetMockAccountId,
                exchangeTransactionId,
                sendCurrency,
                sendAmount,
                receiveCurrency,
                receiveAmount,
                appliedRate,
                feeAmount,
                amountKrw,
                amountUsd,
                reason,
                reasonDetail,
                idempotencyKey
        );
    }

    public void fund(Long sourceMockAccountId) {
        this.sourceMockAccountId = sourceMockAccountId;
        this.status = TransferStatus.FUNDED;
    }

    public void startProcessing() {
        this.status = TransferStatus.PROCESSING;
    }

    public void complete(Long targetMockAccountId) {
        this.targetMockAccountId = targetMockAccountId;
        this.status = TransferStatus.COMPLETED;
    }

    public void cancel() {
        this.status = TransferStatus.CANCELED;
    }

    public void fail(String failureReason) {
        this.status = TransferStatus.FAILED;
        this.failureReason = failureReason;
    }
}
