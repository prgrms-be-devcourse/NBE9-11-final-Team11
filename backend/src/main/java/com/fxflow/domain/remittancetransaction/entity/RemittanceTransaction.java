package com.fxflow.domain.remittancetransaction.entity;

import com.fxflow.domain.remittancetransaction.enums.TransferStatus;
import com.fxflow.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "remittance_transactions", indexes = {
        @Index(name = "idx_remittance_user_created", columnList = "user_id, created_at DESC, id DESC")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RemittanceTransaction extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "journal_id", length = 100)
    private String journalId;

    @Column(name = "recipient_id", nullable = false)
    private Long recipientId;

    @Column(name = "recipient_name", length = 100, nullable = false)
    private String recipientName;

    @Column(name = "recipient_country_code", length = 10, nullable = false)
    private String recipientCountryCode;

    @Column(name = "recipient_currency_code", length = 10, nullable = false)
    private String recipientCurrencyCode;

    @Column(name = "recipient_bank_name", length = 100, nullable = false)
    private String recipientBankName;

    @Column(name = "recipient_account_number", length = 50, nullable = false)
    private String recipientAccountNumber;

    @Column(name = "target_user_id")
    private Long targetUserId;

    @Column(name = "method", length = 30, nullable = false)
    private String method;

    @Column(name = "source_mock_account_id")
    private Long sourceMockAccountId;

    @Column(name = "target_mock_account_id")
    private Long targetMockAccountId;

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
            String recipientName,
            String recipientCountryCode,
            String recipientCurrencyCode,
            String recipientBankName,
            String recipientAccountNumber,
            Long targetUserId,
            String method,
            Long sourceMockAccountId,
            Long targetMockAccountId,
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
            String idempotencyKey,
            String journalId
    ) {
        this.userId = userId;
        this.journalId = journalId;
        this.recipientId = recipientId;
        this.recipientName = recipientName;
        this.recipientCountryCode = recipientCountryCode;
        this.recipientCurrencyCode = recipientCurrencyCode;
        this.recipientBankName = recipientBankName;
        this.recipientAccountNumber = recipientAccountNumber;
        this.targetUserId = targetUserId;
        this.method = method;
        this.sourceMockAccountId = sourceMockAccountId;
        this.targetMockAccountId = targetMockAccountId;
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
            String recipientName,
            String recipientCountryCode,
            String recipientCurrencyCode,
            String recipientBankName,
            String recipientAccountNumber,
            Long targetUserId,
            String method,
            Long sourceMockAccountId,
            Long targetMockAccountId,
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
            String idempotencyKey,
            String journalId
    ) {
        return new RemittanceTransaction(
                userId,
                recipientId,
                recipientName,
                recipientCountryCode,
                recipientCurrencyCode,
                recipientBankName,
                recipientAccountNumber,
                targetUserId,
                method,
                sourceMockAccountId,
                targetMockAccountId,
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
                idempotencyKey,
                journalId
        );
    }

    public static RemittanceTransaction create(
            Long userId,
            Long recipientId,
            Long targetUserId,
            String method,
            Long sourceMockAccountId,
            Long targetMockAccountId,
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
            String idempotencyKey,
            String journalId
    ) {
        return new RemittanceTransaction(
                userId,
                recipientId,
                "",
                "",
                "",
                "",
                "",
                targetUserId,
                method,
                sourceMockAccountId,
                targetMockAccountId,
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
                idempotencyKey,
                journalId
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

    public void refundFailed(String failureReason) {
        this.status = TransferStatus.REFUND_FAILED;
        this.failureReason = failureReason;
    }
}
