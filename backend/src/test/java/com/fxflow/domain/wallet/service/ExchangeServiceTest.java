package com.fxflow.domain.wallet.service;

import com.fxflow.domain.wallet.config.ExchangeFeeProperties;
import com.fxflow.domain.wallet.config.ExchangeProperties;
import com.fxflow.domain.wallet.dto.request.ExchangeQuoteRequest;
import com.fxflow.domain.wallet.dto.response.ExchangeQuoteResponse;
import com.fxflow.domain.wallet.errorcode.ExchangeErrorCode;
import com.fxflow.global.exception.BusinessException;
import com.fxflow.global.fx.ExchangeRateProvider;
import com.fxflow.global.fx.FxRateSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ExchangeServiceTest {

    @Mock
    private ExchangeRateProvider exchangeRateProvider;
    @Mock
    private ExchangeFeeProperties exchangeFeeProperties;
    @Mock
    private ExchangeProperties exchangeProperties;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private ExchangeService exchangeService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("KRW -> USD 환전 견적 시 buyRate가 적용된다")
    void getExchangeQuote_krwToUsd_usesBuyRate() {
        // given
        Long userId = 1L;
        ExchangeQuoteRequest request = new ExchangeQuoteRequest("KRW", "USD", BigDecimal.valueOf(500000));

        FxRateSnapshot snapshot = new FxRateSnapshot(
                "USD", "KRW",
                BigDecimal.valueOf(1350),
                BigDecimal.valueOf(0.01), // 1% spread
                LocalDateTime.now()
        );
        given(exchangeRateProvider.getLatestRate("USD", "KRW"))
                .willReturn(Optional.of(snapshot));

        Map<String, BigDecimal> feeRates = Map.of("KRW-USD", BigDecimal.valueOf(0.005));
        given(exchangeFeeProperties.getRates()).willReturn(feeRates);
        given(exchangeProperties.getQuoteExpirationMinutes()).willReturn(5L);

        // when
        ExchangeQuoteResponse response = exchangeService.getExchangeQuote(userId, request);

        // then
        // buyRate = 1350 * 1.01 = 1363.5
        assertThat(response.exchangeRate()).isEqualByComparingTo(BigDecimal.valueOf(1363.5));
        verify(valueOperations).set(anyString(), any(), any(Duration.class));
    }

    @Test
    @DisplayName("USD -> KRW 환전 견적 시 sellRate가 적용된다")
    void getExchangeQuote_usdToKrw_usesSellRate() {
        // given
        Long userId = 1L;
        ExchangeQuoteRequest request = new ExchangeQuoteRequest("USD", "KRW", BigDecimal.valueOf(100));

        FxRateSnapshot snapshot = new FxRateSnapshot(
                "USD", "KRW",
                BigDecimal.valueOf(1350),
                BigDecimal.valueOf(0.01),
                LocalDateTime.now()
        );
        given(exchangeRateProvider.getLatestRate("USD", "KRW"))
                .willReturn(Optional.of(snapshot));

        Map<String, BigDecimal> feeRates = Map.of("USD-KRW", BigDecimal.valueOf(0.005));
        given(exchangeFeeProperties.getRates()).willReturn(feeRates);
        given(exchangeProperties.getQuoteExpirationMinutes()).willReturn(5L);

        // when
        ExchangeQuoteResponse response = exchangeService.getExchangeQuote(userId, request);

        // then
        // sellRate = 1350 * 0.99 = 1336.5
        assertThat(response.exchangeRate()).isEqualByComparingTo(BigDecimal.valueOf(1336.5));
    }

    @Test
    @DisplayName("환율 조회 실패 시 예외가 발생한다")
    void getExchangeQuote_rateUnavailable() {
        // given
        Long userId = 1L;
        ExchangeQuoteRequest request = new ExchangeQuoteRequest("KRW", "USD", BigDecimal.valueOf(500000));

        given(exchangeRateProvider.getLatestRate("USD", "KRW"))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> exchangeService.getExchangeQuote(userId, request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("수수료 정책이 없는 통화쌍이면 예외가 발생한다")
    void getExchangeQuote_feeRateNotFound() {
        // given
        Long userId = 1L;
        ExchangeQuoteRequest request = new ExchangeQuoteRequest("KRW", "EUR", BigDecimal.valueOf(500000));

        FxRateSnapshot snapshot = new FxRateSnapshot(
                "USD", "KRW",
                BigDecimal.valueOf(1350),
                BigDecimal.valueOf(0.01),
                LocalDateTime.now()
        );
        given(exchangeRateProvider.getLatestRate("USD", "KRW"))
                .willReturn(Optional.of(snapshot));
        given(exchangeFeeProperties.getRates()).willReturn(Map.of());

        // when & then
        assertThatThrownBy(() -> exchangeService.getExchangeQuote(userId, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ExchangeErrorCode.FEE_RATE_NOT_FOUND);
    }

    @Test
    @DisplayName("toAmount, feeAmount, totalAmount이 올바르게 계산된다")
    void getExchangeQuote_calculatesAmountsCorrectly() {
        // given
        Long userId = 1L;
        ExchangeQuoteRequest request = new ExchangeQuoteRequest("KRW", "USD", BigDecimal.valueOf(1000));

        FxRateSnapshot snapshot = new FxRateSnapshot(
                "USD", "KRW",
                BigDecimal.valueOf(2), // mid rate = 2 (단순화)
                BigDecimal.ZERO,       // spread 0 → buyRate = sellRate = 2
                LocalDateTime.now()
        );
        given(exchangeRateProvider.getLatestRate("USD", "KRW"))
                .willReturn(Optional.of(snapshot));
        given(exchangeFeeProperties.getRates())
                .willReturn(Map.of("KRW-USD", BigDecimal.valueOf(0.1))); // 10% fee
        given(exchangeProperties.getQuoteExpirationMinutes()).willReturn(5L);

        // when
        ExchangeQuoteResponse response = exchangeService.getExchangeQuote(userId, request);

        // then
        // toAmount = 1000 * 2 = 2000
        // feeAmount = 2000 * 0.1 = 200
        // totalAmount = 2200
        assertThat(response.fee()).isEqualByComparingTo(BigDecimal.valueOf(200));
        assertThat(response.totalAmount()).isEqualByComparingTo(BigDecimal.valueOf(2200));
    }
}
