package com.fxflow.domain.remittancetransaction.entity;

import com.fxflow.domain.remittancetransaction.enums.VirtualAccountStatus;
import com.fxflow.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Entity
@Getter
@Table(name = "VIRTUAL_ACCOUNTS")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VirtualAccount extends BaseEntity {

    @Column(name = "remittance_transaction_id", nullable = false)
    private Long remittanceTransactionId;

    @Column(name = "account_number", nullable = false, length = 50, unique = true)
    private String accountNumber;

    @Column(name = "expected_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal expectedAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private VirtualAccountStatus status;

    @Builder
    public VirtualAccount(Long remittanceTransactionId, String accountNumber,
                          BigDecimal expectedAmount, VirtualAccountStatus status) {
        this.remittanceTransactionId = remittanceTransactionId;
        this.accountNumber = accountNumber;
        this.expectedAmount = expectedAmount;
        this.status = status != null ? status : VirtualAccountStatus.ISSUED;
    }

    public void updateStatus(VirtualAccountStatus status) {
        this.status = status;
    }
}