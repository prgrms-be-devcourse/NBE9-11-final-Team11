package com.fxflow.domain.wallet.entity;

import com.fxflow.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class CurrencyLot extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "wallet_id")
    private Wallet wallet;
    private String currencyCode;
    @Column(precision = 18, scale = 8, nullable = false)
    private BigDecimal quantity;
    @Column(precision = 18, scale = 8, nullable = false)
    private BigDecimal remainingQuantity; // 아직 쓰지 않은 금액
    @Column(precision = 18, scale = 8, nullable = false)
    private BigDecimal acquisitionRate;
    private String sourceTransactionId;
    @Column(precision = 18, scale = 8, nullable = false)
    private BigDecimal realizedProfit = BigDecimal.ZERO;
    private boolean exhausted = false;

    private CurrencyLot(Wallet wallet, String currencyCode, BigDecimal quantity, BigDecimal acquisitionRate, String sourceTransactionId) {
        this.wallet = wallet;
        this.currencyCode = currencyCode;
        this.quantity = quantity;
        this.remainingQuantity = quantity;
        this.acquisitionRate = acquisitionRate;
        this.sourceTransactionId = sourceTransactionId;
    }

    public static CurrencyLot create(Wallet wallet, String currencyCode, BigDecimal quantity, BigDecimal acquisitionRate, String sourceTransactionId) {
        return new CurrencyLot(wallet, currencyCode, quantity, acquisitionRate, sourceTransactionId);
    }

    public void consume(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) { throw new IllegalArgumentException("Amount must be positive"); }
        if (remainingQuantity.compareTo(amount) < 0) { throw new IllegalStateException("Not enough remaining quantity"); }
        remainingQuantity = remainingQuantity.subtract(amount);
        if (remainingQuantity.compareTo(BigDecimal.ZERO) == 0) {
            exhausted = true;
        }
    }

    public void addRealizedProfit(BigDecimal profit) {
        this.realizedProfit = this.realizedProfit.add(profit);
    }
}