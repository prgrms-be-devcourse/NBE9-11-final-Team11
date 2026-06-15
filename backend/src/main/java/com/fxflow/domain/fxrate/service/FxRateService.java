package com.fxflow.domain.fxrate.service;

import com.fxflow.domain.fxrate.repository.FxRateRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class FxRateService {

    private final FxRateRepository fxRateRepository;

    public FxRateService(FxRateRepository fxRateRepository) {
        this.fxRateRepository = fxRateRepository;
    }

    public BigDecimal getRate(String from, String to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Currency codes must not be null");
        }
        if (from.equals(to)) {
            return BigDecimal.ONE;
        }
        return fxRateRepository
                .findByBaseCurrencyAndQuoteCurrency(from, to)
                .orElseThrow()
            .getMidRate();
    }
}
