package com.fxflow.global.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "currencies")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Currency {

    @Id
    @Column(name = "currency_code", length = 3)
    private String currencyCode;

    @Column(nullable = false, name = "currency_name", length = 100)
    private String currencyName;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(name = "decimal_places", nullable = false)
    private Integer decimalPlaces = 2;

    @CreatedDate
    private LocalDateTime createdAt;

    public Currency(String currencyCode, String currencyName, String symbol) {
        this.currencyCode = currencyCode;
        this.currencyName = currencyName;
        this.symbol = symbol;
    }
}
