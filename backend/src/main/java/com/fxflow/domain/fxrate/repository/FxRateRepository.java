package com.fxflow.domain.fxrate.repository;

import com.fxflow.domain.fxrate.entity.FxRate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FxRateRepository extends JpaRepository<FxRate, Long> {

}
