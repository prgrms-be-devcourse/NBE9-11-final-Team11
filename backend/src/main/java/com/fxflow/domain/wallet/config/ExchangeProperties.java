package com.fxflow.domain.wallet.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "exchange")
@Getter
@Setter
public class ExchangeProperties {
    private long quoteExpirationMinutes;
}
