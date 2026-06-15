package com.fxflow.domain.remittancetransaction.entity;

import com.fxflow.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "recipients")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Recipient extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "bank_code", length = 20, nullable = false)
    private String bankCode;

    @Column(name = "account_number", length = 50, nullable = false, unique = true)
    private String accountNumber;

    private Recipient(Long userId, String name, String bankCode, String accountNumber) {
        this.userId = userId;
        this.name = name;
        this.bankCode = bankCode;
        this.accountNumber = accountNumber;
    }

    public static Recipient create(Long userId, String name, String bankCode, String accountNumber) {
        return new Recipient(userId, name, bankCode, accountNumber);
    }
}