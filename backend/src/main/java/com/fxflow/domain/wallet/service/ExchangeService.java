package com.fxflow.domain.wallet.service;

import com.fxflow.domain.wallet.config.ExchangeFeeProperties;
import com.fxflow.domain.wallet.config.ExchangeProperties;
import com.fxflow.domain.wallet.dto.cache.ExchangeQuoteCache;
import com.fxflow.domain.wallet.dto.request.ExchangeQuoteRequest;
import com.fxflow.domain.wallet.dto.response.ExchangeQuoteResponse;
import com.fxflow.domain.wallet.errorcode.ExchangeErrorCode;
import com.fxflow.global.exception.BusinessException;
import com.fxflow.global.fx.ExchangeRateProvider;
import com.fxflow.global.fx.FxRateSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExchangeService {

    private final ExchangeRateProvider exchangeRateProvider;
    private final ExchangeFeeProperties exchangeFeeProperties;
    private final ExchangeProperties exchangeProperties;
    private final RedisTemplate<String, Object> redisTemplate;

    public ExchangeQuoteResponse getExchangeQuote(Long userId, ExchangeQuoteRequest request) {

        /*
         * base=USD, quote=KRW로 고정
         * from == "USD"(USD→KRW, base 매도) → sellRate,
         * from == "KRW"(KRW→USD, base 매수) → buyRate,
         * */

        String fromCurrency = request.fromCurrency();
        String toCurrency = request.toCurrency();
        BigDecimal amount = request.amount();

        FxRateSnapshot fxRateSnapshot = exchangeRateProvider.getLatestRate("USD", "KRW")
                .orElseThrow( () -> new BusinessException(ExchangeErrorCode.FEE_RATE_NOT_FOUND));
        BigDecimal appliedRate = fromCurrency.equals("USD") ? fxRateSnapshot.sellRate() : fxRateSnapshot.buyRate(); // todo: 현재 USD <-> WON 고정, 추후 다른 환율 추가 시 수정 필요

        BigDecimal feeRate = exchangeFeeProperties.getRates().get(fromCurrency + "-" + toCurrency);
        if (feeRate == null) {
            throw new BusinessException(ExchangeErrorCode.FEE_RATE_NOT_FOUND);
        }

        BigDecimal toAmount = amount.multiply(appliedRate);
        BigDecimal feeAmount = toAmount.multiply(feeRate);
        BigDecimal totalAmount = toAmount.add(feeAmount);
        LocalDateTime expiredAt =
                LocalDateTime.now()
                        .plusMinutes(exchangeProperties.getQuoteExpirationMinutes());
        String quoteId = UUID.randomUUID().toString();

        // Quote redis에 저장
        ExchangeQuoteCache cache = new ExchangeQuoteCache(
                userId, fromCurrency, toCurrency, amount, toAmount, appliedRate, feeAmount, totalAmount
        );
        redisTemplate.opsForValue().set(
                "quote:" + quoteId,
                cache,
                Duration.ofMinutes(exchangeProperties.getQuoteExpirationMinutes())
        );

        return new ExchangeQuoteResponse(
                amount,
                toAmount,
                appliedRate,
                feeAmount,
                totalAmount,
                expiredAt,
                quoteId
        );
    }
}