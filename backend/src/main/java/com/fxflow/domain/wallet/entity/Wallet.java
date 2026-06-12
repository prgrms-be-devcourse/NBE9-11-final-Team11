package com.fxflow.domain.wallet.entity;

import com.fxflow.domain.user.entity.User;
import com.fxflow.global.entity.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;

@Entity
@Getter
public class Wallet extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

}