package com.fxflow.domain.wallet.entity;

import com.fxflow.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "p2p_transfers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class P2pTransfer extends BaseEntity {

    @Column(name = "transfer_id", length = 50, nullable = false, unique = true)
    private String transferId;

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

    @Column(length = 255)
    private String memo;

    private P2pTransfer(String transferId, Wallet fromWallet, Wallet toWallet, String currencyCode, BigDecimal amount, String memo) {
        this.transferId = transferId;
        this.fromWallet = fromWallet;
        this.toWallet = toWallet;
        this.currencyCode = currencyCode;
        this.amount = amount;
        this.memo = memo;
    }

    public static P2pTransfer create(
            String transferId,
            Wallet fromWallet,
            Wallet toWallet,
            String currencyCode,
            BigDecimal amount,
            String memo
    ) {
        return new P2pTransfer(
                transferId,
                fromWallet,
                toWallet,
                currencyCode,
                amount,
                memo);
    }

    public static String generateTransferId() {
        return "TXN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

}