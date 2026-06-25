package com.fxflow.domain.wallet.service;

import com.fxflow.domain.wallet.entity.CurrencyLot;
import com.fxflow.domain.wallet.entity.Wallet;
import com.fxflow.domain.wallet.errorcode.LotErrorCode;
import com.fxflow.domain.wallet.repository.CurrencyLotRepository;
import com.fxflow.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CurrencyLotService {
    private final CurrencyLotRepository currencyLotRepository;

    public BigDecimal consumeLots(Wallet wallet, BigDecimal amount, BigDecimal saleRate) {
        List<CurrencyLot> lots = currencyLotRepository.findAvailableLotsFIFO(wallet.getId());
        BigDecimal remaining = amount;
        BigDecimal totalRealizedProfit = BigDecimal.ZERO;

        for (CurrencyLot lot : lots) {
            if (remaining.compareTo(BigDecimal.ZERO) == 0) break;
            BigDecimal consumeAmount = lot.getRemainingQuantity().min(remaining);
            BigDecimal profit = saleRate.subtract(lot.getAcquisitionRate()).multiply(consumeAmount);
            lot.addRealizedProfit(profit);
            totalRealizedProfit = totalRealizedProfit.add(profit);
            lot.consume(consumeAmount);
            remaining = remaining.subtract(consumeAmount);
        }

        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessException(LotErrorCode.INSUFFICIENT_LOT_BALANCE);
        }

        currencyLotRepository.saveAll(lots);
        return totalRealizedProfit;
    }

    // exchange settle lots
    public void settleLots(Wallet fromWallet, Wallet toWallet, BigDecimal amount, BigDecimal rate, String sourceTransactionId) {
        if (fromWallet.getCurrencyCode().equals("USD")) {
            consumeLots(fromWallet, amount, rate);
        }
        if (toWallet.getCurrencyCode().equals("USD")) {
            CurrencyLot newLot = CurrencyLot.create(toWallet, toWallet.getCurrencyCode(), amount, rate, sourceTransactionId);
            currencyLotRepository.save(newLot);
        }
    }

    // p2p settle lots
    public void settleLots(Wallet fromWallet, Wallet toWallet, BigDecimal amount, String sourceTransactionId) {
        BigDecimal acquisitionRate = BigDecimal.ZERO;
        if (fromWallet.getCurrencyCode().equals("USD")) {
            consumeLots(fromWallet, amount, BigDecimal.ZERO); // p2p는 이익 없음
        }
        if (toWallet.getCurrencyCode().equals("USD")) {
            CurrencyLot newLot = CurrencyLot.create(toWallet, toWallet.getCurrencyCode(), amount, acquisitionRate, sourceTransactionId);
            currencyLotRepository.save(newLot);
        }
    }

}
