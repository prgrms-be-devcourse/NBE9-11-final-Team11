package com.fxflow.domain.mockbankaccount.entity;

import com.fxflow.domain.mockbankaccount.errorcode.MockBankAccountErrorCode;
import com.fxflow.domain.mockbankaccount.enums.MockBankAccountOwnerType;
import com.fxflow.domain.user.entity.User;
import com.fxflow.global.entity.BaseEntity;
import com.fxflow.global.exception.BusinessException;
import com.fxflow.global.util.KstClock;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "mock_bank_accounts",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_mock_bank_account_user_currency", columnNames = {"user_id", "currency_code"}),
                @UniqueConstraint(
                        name = "uk_mock_bank_account_bank_number_currency",
                        columnNames = {"bank_name", "account_number", "currency_code"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class MockBankAccount extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, length = 20)
    private MockBankAccountOwnerType ownerType;

    @Column(name = "currency_code", nullable = false, length = 10)
    private String currencyCode;        // KRW: 사용자가 직접 연결 / USD: 시딩 데이터(받는 가상계좌)

    @Column(name = "bank_name", nullable = false, length = 100)
    private String bankName;

    @Column(name = "account_number", nullable = false, length = 50)
    private String accountNumber;

    @Column(name = "account_holder_name", nullable = false, length = 100)
    private String accountHolderName;

    @Column(nullable = false, precision = 18, scale = 8)
    private BigDecimal balance;

    @Version
    @Column(nullable = false)
    private Long version;            // 낙관적 락 — Integer

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;    // soft delete — NULL 허용

    public static MockBankAccount create(User user, String bankName, String accountNumber) {
        MockBankAccount account = new MockBankAccount();
        account.user = user;
        account.ownerType = MockBankAccountOwnerType.USER;
        account.currencyCode = "KRW";
        account.bankName = bankName;
        account.accountNumber = accountNumber;
        account.accountHolderName = user.getName();
        account.balance = new BigDecimal("10000000"); // 초기 1,000만원
        return account;
    }

    /**
     * USD 등 시딩용 가상계좌 생성 (받는 계좌 역할, 운영진이 미리 데이터 등록)
     */
    public static MockBankAccount createSeedAccount(
            User user, String currencyCode, String bankName, String accountNumber, BigDecimal initialBalance
    ) {
        MockBankAccount account = new MockBankAccount();
        account.user = user;
        account.ownerType = MockBankAccountOwnerType.USER;
        account.currencyCode = currencyCode;
        account.bankName = bankName;
        account.accountNumber = accountNumber;
        account.accountHolderName = user.getName();
        account.balance = initialBalance;
        return account;
    }

    /**
     * 수취인 지급 시뮬레이션용 모의계좌 생성.
     * 수취인은 플랫폼 회원이 아니므로 User를 연결하지 않고 계좌 자체의 예금주명을 저장한다.
     */
    public static MockBankAccount createRecipientAccount(
            String accountHolderName,
            String currencyCode,
            String bankName,
            String accountNumber,
            BigDecimal initialBalance
    ) {
        MockBankAccount account = new MockBankAccount();
        account.ownerType = MockBankAccountOwnerType.RECIPIENT;
        account.currencyCode = currencyCode;
        account.bankName = bankName;
        account.accountNumber = accountNumber;
        account.accountHolderName = accountHolderName;
        account.balance = initialBalance;
        return account;
    }


    // 입금
    public void deposit(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(MockBankAccountErrorCode.MOCK_ACCOUNT_INVALID_AMOUNT);
        }
        this.balance = this.balance.add(amount);
    }

    // 출금
    public void withdraw(BigDecimal amount) {
        if (this.balance.compareTo(amount) < 0) {
            throw new BusinessException(MockBankAccountErrorCode.MOCK_ACCOUNT_INSUFFICIENT_BALANCE);
        }
        this.balance = this.balance.subtract(amount);
    }

    // soft delete
    public void delete() {
        this.deletedAt = KstClock.now();
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }
}
