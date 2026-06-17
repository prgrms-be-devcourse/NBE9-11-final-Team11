package com.fxflow.domain.wallet.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "exchange.fee")
@Getter
@Setter
public class ExchangeFeeProperties {
    private Map<String, BigDecimal> rates = new HashMap<>();
}