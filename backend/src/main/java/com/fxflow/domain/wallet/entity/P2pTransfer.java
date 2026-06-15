package com.fxflow.domain.wallet.entity;

import com.fxflow.domain.remittancetransaction.enums.TransferStatus;
import com.fxflow.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "p2p_transfers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class P2pTransfer extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "from_wallet_id", nullable = false)
    private Wallet fromWallet;

    @ManyToOne
    @JoinColumn(name = "to_wallet_id", nullable = false)
    private Wallet toWallet;

    @Column(name = "currency_code", length = 3, nullable = false)
    private String currencyCode;

    @Column(precision = 18, scale = 4, nullable = false)
    private BigDecimal amount;

    private String memo;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private TransferStatus status;

    private P2pTransfer(Wallet fromWallet, Wallet toWallet, String currencyCode, BigDecimal amount, String memo) {
        this.fromWallet = fromWallet;
        this.toWallet = toWallet;
        this.currencyCode = currencyCode;
        this.amount = amount;
        this.memo = memo;
        this.status = TransferStatus.PENDING;
    }

    public static P2pTransfer create(
            Wallet fromWallet,
            Wallet toWallet,
            String currencyCode,
            BigDecimal amount,
            String memo
    ) {
        return new P2pTransfer(
                fromWallet,
                toWallet,
                currencyCode,
                amount,
                memo
        );
    }

    public void complete() {
        this.status = TransferStatus.COMPLETED;
    }

    public void fail() {
        this.status = TransferStatus.FAILED;
    }

}