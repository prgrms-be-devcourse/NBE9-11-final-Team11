package com.fxflow.domain.wallet.repository;

import com.fxflow.domain.wallet.entity.CurrencyLot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface CurrencyLotRepository extends JpaRepository<CurrencyLot, Long> {
    List<CurrencyLot> findByWallet_Id(Long id);

    @Query("SELECT l FROM CurrencyLot l WHERE l.wallet.id = :walletId AND l.exhausted = false ORDER BY l.createdAt ASC, l.id ASC")
    List<CurrencyLot> findAvailableLotsFIFO(@Param("walletId") Long walletId);

    @Query("SELECT COALESCE(SUM(l.realizedProfit), 0) FROM CurrencyLot l WHERE l.wallet.id = :walletId")
    BigDecimal sumRealizedProfitByWalletId(@Param("walletId") Long walletId);
}
