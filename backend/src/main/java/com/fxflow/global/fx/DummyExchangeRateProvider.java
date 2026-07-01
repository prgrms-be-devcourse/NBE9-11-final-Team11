package com.fxflow.global.fx;

import com.fxflow.global.util.KstClock;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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
                        KstClock.now()
                )
        );
    }

    @Override
    public Optional<FxRateSnapshot> getLatestRateOrThrowIfStale(
            String baseCurrency,
            String quoteCurrency
    ) {
        return getLatestRate(baseCurrency, quoteCurrency);
    }
}