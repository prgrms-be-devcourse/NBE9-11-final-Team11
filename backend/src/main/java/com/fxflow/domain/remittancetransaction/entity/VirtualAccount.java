package com.fxflow.domain.remittancetransaction.entity;

import com.fxflow.domain.remittancetransaction.enums.VirtualAccountStatus;
import com.fxflow.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Entity
@Table(name = "virtual_accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VirtualAccount extends BaseEntity {

    @Column(name = "remittance_transaction_id", nullable = false)
    private Long remittanceTransactionId;

    @Column(name = "account_number", length = 50, nullable = false, unique = true)
    private String accountNumber;

    @Column(name = "expected_amount", precision = 18, scale = 2, nullable = false)
    private BigDecimal expectedAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private VirtualAccountStatus status;

    private VirtualAccount(Long remittanceTransactionId, String accountNumber, BigDecimal expectedAmount) {
        this.remittanceTransactionId = remittanceTransactionId;
        this.accountNumber = accountNumber;
        this.expectedAmount = expectedAmount;
        this.status = VirtualAccountStatus.ISSUED; // 초기 계좌 발급 상태 고정
    }

    public static VirtualAccount create(Long remittanceTransactionId, String accountNumber, BigDecimal expectedAmount) {
        return new VirtualAccount(remittanceTransactionId, accountNumber, expectedAmount);
    }

    public void updateStatus(VirtualAccountStatus status) {
        this.status = status;
    }
}