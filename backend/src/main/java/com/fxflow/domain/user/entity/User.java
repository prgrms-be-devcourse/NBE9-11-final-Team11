package com.fxflow.domain.user.entity;

import com.fxflow.domain.wallet.entity.Wallet;
import com.fxflow.global.entity.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @OneToMany(mappedBy = "user")
    private List<Wallet> wallets = new ArrayList<>();

}
