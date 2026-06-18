package com.fxflow.domain.wallet.entity;

import com.fxflow.domain.user.entity.User;
import com.fxflow.domain.wallet.enums.ExchangeStatus;
import com.fxflow.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "exchange_transactions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExchangeTransaction extends BaseEntity {

    @Column(unique = true, nullable = false)
    private String transactionId;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne
    @JoinColumn(name = "from_wallet_id")
    private Wallet fromWallet;

    @ManyToOne
    @JoinColumn(name = "to_wallet_id")
    private Wallet toWallet;

    @Column(name = "from_currency_code", length = 3, nullable = false)
    private String fromCurrencyCode;

    @Column(name = "to_currency_code", length = 3, nullable = false)
    private String toCurrencyCode;

    @Column(name = "from_amount", precision = 18, scale = 2, nullable = false)
    private BigDecimal fromAmount;

    @Column(name = "to_amount", precision = 18, scale = 2, nullable = false)
    private BigDecimal toAmount;

    @Column(name = "base_rate", precision = 18, scale = 8, nullable = false)
    private BigDecimal baseRate;

    @Column(name = "spread_rate", precision = 18, scale = 8, nullable = false)
    private BigDecimal spreadRate;

    @Column(name = "final_rate", precision = 18, scale = 8, nullable = false)
    private BigDecimal finalRate;

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    private ExchangeStatus status;

    @Column(length = 100, nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "fee_amount", nullable = false)
    private BigDecimal feeAmount;


    private ExchangeTransaction(User user, Wallet fromWallet, Wallet toWallet, String fromCurrencyCode, String toCurrencyCode, BigDecimal fromAmount, BigDecimal toAmount, BigDecimal baseRate, BigDecimal spreadRate, BigDecimal finalRate, ExchangeStatus status, String idempotencyKey, BigDecimal feeAmount) {
        this.transactionId = "EX_" + java.util.UUID.randomUUID().toString().replace("-", "");
        this.user = user;
        this.fromWallet = fromWallet;
        this.toWallet = toWallet;
        this.fromCurrencyCode = fromCurrencyCode;
        this.toCurrencyCode = toCurrencyCode;
        this.fromAmount = fromAmount;
        this.toAmount = toAmount;
        this.baseRate = baseRate;
        this.spreadRate = spreadRate;
        this.finalRate = finalRate;
        this.status = status;
        this.idempotencyKey = idempotencyKey;
        this.feeAmount = feeAmount;
    }

    public static ExchangeTransaction create(User user, Wallet fromWallet, Wallet toWallet, String fromCurrencyCode, String toCurrencyCode, BigDecimal fromAmount, BigDecimal toAmount, BigDecimal baseRate, BigDecimal spreadRate, BigDecimal finalRate, ExchangeStatus status, String idempotencyKey, BigDecimal feeAmount) {
        return new ExchangeTransaction(user, fromWallet, toWallet, fromCurrencyCode, toCurrencyCode, fromAmount, toAmount, baseRate, spreadRate, finalRate, status, idempotencyKey, feeAmount);
    }
}