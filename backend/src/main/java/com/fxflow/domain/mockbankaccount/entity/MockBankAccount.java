package com.fxflow.domain.mockbankaccount.entity;

import com.fxflow.global.entity.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "mock_bank_accounts")
public class MockBankAccount extends BaseEntity {
}
