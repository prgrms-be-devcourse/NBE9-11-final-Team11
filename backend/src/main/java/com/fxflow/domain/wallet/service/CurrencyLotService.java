package com.fxflow.domain.wallet.service;

import com.fxflow.domain.wallet.entity.CurrencyLot;
import com.fxflow.domain.wallet.entity.Wallet;
import com.fxflow.domain.wallet.errorcode.LotErrorCode;
import com.fxflow.domain.wallet.repository.CurrencyLotRepository;
import com.fxflow.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
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
    public void settleLots(Wallet fromWallet, Wallet toWallet, BigDecimal fromAmount, BigDecimal toAmount, BigDecimal rate, String sourceTransactionId) {
        if (fromWallet.getCurrencyCode().equals("USD")) {
            consumeLots(fromWallet, fromAmount, rate);
        }
        if (toWallet.getCurrencyCode().equals("USD")) {
            CurrencyLot newLot = CurrencyLot.create(toWallet, toWallet.getCurrencyCode(), toAmount, rate, sourceTransactionId);
            currencyLotRepository.save(newLot);
        }
    }

    // p2p settle lots
    public void settleLots(Wallet fromWallet, Wallet toWallet, BigDecimal amount, String sourceTransactionId) {
        if (fromWallet.getCurrencyCode().equals("USD") && toWallet.getCurrencyCode().equals("USD")) {
            transferLotsForP2p(fromWallet, toWallet, amount, sourceTransactionId);
        }
    }

    private void transferLotsForP2p(Wallet sender, Wallet receiver, BigDecimal amount, String sourceTransactionId) {
        List<CurrencyLot> senderLots = currencyLotRepository.findAvailableLotsFIFO(sender.getId());
        List<CurrencyLot> receiverLots = new ArrayList<>();
        BigDecimal remaining = amount;

        for (CurrencyLot lot : senderLots) {
            if (remaining.compareTo(BigDecimal.ZERO) == 0) break;

            BigDecimal consumeAmount = lot.getRemainingQuantity().min(remaining);
            lot.consume(consumeAmount);

            CurrencyLot receiverLot = CurrencyLot.create(
                    receiver,
                    lot.getCurrencyCode(),
                    consumeAmount,
                    lot.getAcquisitionRate(),
                    sourceTransactionId);

            receiverLots.add(receiverLot);
            remaining = remaining.subtract(consumeAmount);
        }

        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessException(LotErrorCode.INSUFFICIENT_LOT_BALANCE);
        }

        currencyLotRepository.saveAll(senderLots);
        currencyLotRepository.saveAll(receiverLots);
    }
}