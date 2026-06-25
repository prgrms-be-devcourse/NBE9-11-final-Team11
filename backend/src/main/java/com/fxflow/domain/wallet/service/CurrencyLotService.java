package com.fxflow.domain.wallet.service;

import com.fxflow.domain.wallet.entity.CurrencyLot;
import com.fxflow.domain.wallet.entity.Wallet;
import com.fxflow.domain.wallet.errorcode.LotErrorCode;
import com.fxflow.domain.wallet.repository.CurrencyLotRepository;
import com.fxflow.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CurrencyLotService {
    public record LotsConsumeResult(BigDecimal realizedProfit, BigDecimal weightedAvgAcquisitionRate) {}
    private final CurrencyLotRepository currencyLotRepository;

    public LotsConsumeResult consumeLots(Wallet wallet, BigDecimal amount, BigDecimal saleRate) {
        List<CurrencyLot> lots = currencyLotRepository.findAvailableLotsFIFO(wallet.getId());
        BigDecimal remaining = amount;
        BigDecimal totalRealizedProfit = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;

        for (CurrencyLot lot : lots) {
            if (remaining.compareTo(BigDecimal.ZERO) == 0) break;
            BigDecimal consumeAmount = lot.getRemainingQuantity().min(remaining);
            BigDecimal profit = saleRate.subtract(lot.getAcquisitionRate()).multiply(consumeAmount);
            totalCost = totalCost.add(lot.getAcquisitionRate().multiply(consumeAmount));
            lot.addRealizedProfit(profit);
            totalRealizedProfit = totalRealizedProfit.add(profit);
            lot.consume(consumeAmount);
            remaining = remaining.subtract(consumeAmount);
        }

        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessException(LotErrorCode.INSUFFICIENT_LOT_BALANCE);
        }

        currencyLotRepository.saveAll(lots);
        BigDecimal weightedAvgRate = totalCost.divide(amount, 10, RoundingMode.HALF_UP);
        return new LotsConsumeResult(totalRealizedProfit, weightedAvgRate);
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
        // 통화 불일치는 transfer service에서 이미 검증됨
        // 여기서는 USD 지갑에 대해서만 lot 처리
        BigDecimal acquisitionRate = BigDecimal.ZERO;

        if (fromWallet.getCurrencyCode().equals("USD")) {
            LotsConsumeResult result = consumeLots(fromWallet, amount, BigDecimal.ZERO); // no saleRate for p2p
            acquisitionRate = result.weightedAvgAcquisitionRate(); // inherit sender's cost basis
        }
        if (toWallet.getCurrencyCode().equals("USD")) {
            CurrencyLot newLot = CurrencyLot.create(toWallet, toWallet.getCurrencyCode(), amount, acquisitionRate, sourceTransactionId);
            currencyLotRepository.save(newLot);
        }
    }

}
