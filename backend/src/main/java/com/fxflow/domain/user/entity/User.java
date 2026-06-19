package com.fxflow.domain.user.entity;

import com.fxflow.domain.transactionlimit.enums.LimitTier;
import com.fxflow.domain.user.enums.UserRole;
import com.fxflow.domain.user.enums.UserStatus;
import com.fxflow.domain.wallet.entity.Wallet;
import com.fxflow.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class User extends BaseEntity {
    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status;

    @Column(nullable = false, length = 30)
    private String kycStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "limit_tier", nullable = false, length = 20)
    private LimitTier limitTier;

    @Column(nullable = false, precision = 18, scale = 8)
    private BigDecimal walletLimitKrw;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<Wallet> wallets = new ArrayList<>();

    public static User create(String email, String passwordHash, String name) {
        User user = new User();
        user.email = email;
        user.passwordHash = passwordHash;
        user.name = name;
        user.status = UserStatus.ACTIVE;
        user.kycStatus = "PENDING";
        user.role = UserRole.USER;
        user.limitTier = LimitTier.STANDARD;
        user.walletLimitKrw = new BigDecimal("2000000");
        return user;
    }

    // 회원 탈퇴
    public void withdraw(String maskedEmail,String maskedName) {
        this.status=UserStatus.WITHDRAWN;
        this.email=maskedEmail;
        this.name=maskedName;
        this.passwordHash="";
    }
    // 한도 증액 완료 시 호출
    public void upgradeTier() {
        this.limitTier = LimitTier.ENHANCED;
        this.walletLimitKrw = new BigDecimal("3000000");
    }
}