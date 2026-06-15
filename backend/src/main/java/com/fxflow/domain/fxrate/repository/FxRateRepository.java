package com.fxflow.domain.fxrate.repository;

import com.fxflow.domain.fxrate.entity.FxRate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FxRateRepository extends JpaRepository<FxRate, Long> {

    Optional<FxRate> findByBaseCurrencyAndQuoteCurrency(
            String fromCurrency,
            String toCurrency
    );
}
