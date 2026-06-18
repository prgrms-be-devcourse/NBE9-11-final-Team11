package com.fxflow.domain.wallet.entity;

import com.fxflow.domain.user.entity.User;
import com.fxflow.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "wallets")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Wallet extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "currency_code", length = 3, nullable = false)
    private String currencyCode;

    @Column(nullable = false)
    private BigDecimal balance = BigDecimal.ZERO;;

    @Version
    @Column(nullable = false)
    private Long version;

    private LocalDateTime deletedAt;

    private Wallet(User user, String currencyCode, BigDecimal balance) {
        this.user = user;
        this.currencyCode = currencyCode;
        this.balance = (balance == null) ? BigDecimal.ZERO : balance;
    }

    public static Wallet create(User user, String currencyCode, BigDecimal balance) {
        return new Wallet(user, currencyCode, balance);
    }

    public BigDecimal deposit(BigDecimal amount) {
        this.balance = this.balance.add(amount);
        return balance;
    }

    public BigDecimal withdraw(BigDecimal amount) {
        this.balance = this.balance.subtract(amount);
        return balance;
    }

    public BigDecimal deposit(BigDecimal amount, String currencyCode) {
        if (this.currencyCode.equals(currencyCode)) {
            this.deposit(amount);
        }
        return balance;
    }

    public void delete() {
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

}