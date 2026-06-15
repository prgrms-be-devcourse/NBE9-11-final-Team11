package com.fxflow.domain.remittancetransaction.entity;

import com.fxflow.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "RECIPIENTS")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AttributeOverride(name = "id", column = @Column(name = "recipient_id")) // 실제 ERD PK 컬럼명 명시
public class Recipient extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "bank_code", nullable = false, length = 20)
    private String bankCode;

    @Column(name = "account_number", nullable = false, length = 50, unique = true)
    private String accountNumber;

    @Builder
    public Recipient(Long userId, String name, String bankCode, String accountNumber) {
        this.userId = userId;
        this.name = name;
        this.bankCode = bankCode;
        this.accountNumber = accountNumber;
    }
}