package com.fxflow.domain.remittancetransaction.entity;

import com.fxflow.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "recipients",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_recipient_user_bank_account",
                columnNames = {"user_id", "country_code", "bank_name", "account_number"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Recipient extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "target_user_id")
    private Long targetUserId;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "country_code", length = 10, nullable = false)
    private String countryCode;

    @Column(name = "currency_code", length = 10, nullable = false)
    private String currencyCode;

    @Column(name = "bank_name", length = 100, nullable = false)
    private String bankName;

    @Column(name = "account_number", length = 50, nullable = false)
    private String accountNumber;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    private Recipient(
            Long userId,
            Long targetUserId,
            String name,
            String countryCode,
            String currencyCode,
            String bankName,
            String accountNumber
    ) {
        this.userId = userId;
        this.targetUserId = targetUserId;
        this.name = name;
        this.countryCode = countryCode;
        this.currencyCode = currencyCode;
        this.bankName = bankName;
        this.accountNumber = accountNumber;
    }

    public static Recipient create(
            Long userId,
            String name,
            String countryCode,
            String currencyCode,
            String bankName,
            String accountNumber
    ) {
        return new Recipient(
                userId,
                null,
                name,
                countryCode,
                currencyCode,
                bankName,
                accountNumber
        );
    }

    /**
     * 수취인 주소록에서 더 이상 사용하지 않도록 Soft Delete 처리한다.
     * 과거 송금 내역은 RemittanceTransaction의 수취인 스냅샷으로 보존한다.
     */
    public void delete(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }
}
