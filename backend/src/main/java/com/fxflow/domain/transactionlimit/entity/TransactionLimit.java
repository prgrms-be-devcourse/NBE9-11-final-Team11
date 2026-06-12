package com.fxflow.domain.transactionlimit.entity;

import com.fxflow.global.entity.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "transaction_limits")
public class TransactionLimit extends BaseEntity {
}
