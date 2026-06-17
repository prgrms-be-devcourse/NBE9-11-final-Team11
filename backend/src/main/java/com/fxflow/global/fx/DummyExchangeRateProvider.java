package com.fxflow.global.fx;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class DummyExchangeRateProvider implements ExchangeRateProvider {

    @Override
    public Optional<FxRateSnapshot> getLatestRate(
            String baseCurrency,
            String quoteCurrency
    ) {
        return Optional.of(
                new FxRateSnapshot(
                        baseCurrency,
                        quoteCurrency,
                        new BigDecimal("1350"),
                        new BigDecimal("0.001"),
                        LocalDateTime.now()
                )
        );
    }
}