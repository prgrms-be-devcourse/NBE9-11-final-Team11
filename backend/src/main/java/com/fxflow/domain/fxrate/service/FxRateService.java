package com.fxflow.domain.fxrate.service;

import com.fxflow.domain.fxrate.dto.response.TwelveDataResponse;
import com.fxflow.domain.fxrate.entity.FxRate;
import com.fxflow.domain.fxrate.exception.FxRateErrorCode;
import com.fxflow.domain.fxrate.repository.FxRateRepository;
import com.fxflow.global.exception.BusinessException;
import com.fxflow.global.fx.FxRateSnapshot;
import com.fxflow.global.fx.FxRateUpdatedEvent;
import com.fxflow.global.util.KstClock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class FxRateService {

    private static final String BASE_CURRENCY = "USD";
    private static final String QUOTE_CURRENCY = "KRW";
    private static final String SYMBOL = "USD/KRW";
    private static final String SOURCE = "TwelveData";
    private static final String EXCHANGE_RATE_URL =
            "https://api.twelvedata.com/exchange_rate?symbol={symbol}&apikey={apikey}";

    private final FxRateRepository fxRateRepository;
    private final RestClient restClient;
    private final ApplicationEventPublisher eventPublisher;
    private final String apiKey;
    private final long stalenessThresholdMinutes;

    // 마지막으로 외부 API 호출에 성공한 서버 시각. 서버 기동 시각으로 초기화한다.
    // (null로 두면 "재시작 직후 API가 이미 죽어있는" 경우 첫 수집 성공 전까지 신선도 검증이 무기한 무력화됨)
    private final AtomicReference<Instant> lastSuccessCallAt = new AtomicReference<>(Instant.now());

    public FxRateService(FxRateRepository fxRateRepository,
                         ApplicationEventPublisher eventPublisher,
                         RestClient.Builder restClientBuilder,
                         @Value("${twelvedata.api-key:}") String apiKey,
                         @Value("${fxrate.staleness-threshold-minutes}") long stalenessThresholdMinutes) {
        this.fxRateRepository = fxRateRepository;
        // Spring Boot가 자동 구성한 RestClient.Builder를 주입받아 사용한다.
        // (메시지 컨버터·Observation·타임아웃 등 자동 설정 상속, 테스트에서도 빌더 직접 주입 가능)
        this.restClient = restClientBuilder.build();
        this.eventPublisher = eventPublisher;
        this.apiKey = apiKey;
        this.stalenessThresholdMinutes = stalenessThresholdMinutes;
    }

    // Twelve Data에서 USD/KRW 기준 환율을 수집해 저장하고, 갱신 이벤트를 발행한다. (스케줄러가 주기적으로 호출 예정)
    @Transactional
    public void collectUsdKrwRate() {
        TwelveDataResponse response = fetchUsdKrwRate();
        if (response == null || response.rate() == null) {
            throw new BusinessException(FxRateErrorCode.FX_RATE_FETCH_FAILED);
        }

        FxRate fxRate = FxRate.create(
                BASE_CURRENCY,
                QUOTE_CURRENCY,
                response.rate(),                  // 기준 환율(mid)
                SOURCE,
                toFetchedAt(response.timestamp())
        );
        fxRateRepository.save(fxRate);
        lastSuccessCallAt.set(Instant.now());

        eventPublisher.publishEvent(new FxRateUpdatedEvent(
                new FxRateSnapshot(
                        fxRate.getBaseCurrency(),
                        fxRate.getQuoteCurrency(),
                        fxRate.getMidRate(),
                        fxRate.getSpread(),
                        fxRate.getFetchedAt()
                )
        ));

        log.info("환율 수집 완료. symbol={}, midRate={}, fetchedAt={}", SYMBOL, fxRate.getMidRate(), fxRate.getFetchedAt());
    }

    public BigDecimal getRate(String from, String to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Currency codes must not be null");
        }
        if (from.equals(to)) {
            return BigDecimal.ONE;
        }
        validateFreshness();
        return fxRateRepository
                .findFirstByBaseCurrencyAndQuoteCurrencyOrderByFetchedAtDesc(from, to)
                .orElseThrow(() -> new BusinessException(FxRateErrorCode.FX_RATE_NOT_FOUND))
                .getMidRate();
    }

    // 환율 신선도 검증 — 마지막 API 성공 호출(또는 서버 기동)이 임계값보다 오래되면 장애로 간주해 거래를 차단한다.
    // (주말/공휴일 등 시장 휴장 여부와 무관하게, API 호출 성공 여부만으로 판정)
    public void validateFreshness() {
        Instant lastCall = lastSuccessCallAt.get();
        long minutesSinceLastCall = Duration.between(lastCall, Instant.now()).toMinutes();
        if (minutesSinceLastCall > stalenessThresholdMinutes) {
            throw new BusinessException(FxRateErrorCode.FX_RATE_STALE);
        }
    }

    private TwelveDataResponse fetchUsdKrwRate() {
        try {
            return restClient.get()
                    .uri(EXCHANGE_RATE_URL, SYMBOL, apiKey)
                    .retrieve()
                    .body(TwelveDataResponse.class);
        } catch (RestClientException e) {
            log.warn("Twelve Data 환율 조회 실패. symbol={}", SYMBOL, e);
            throw new BusinessException(FxRateErrorCode.FX_RATE_FETCH_FAILED, e);
        }
    }

    private LocalDateTime toFetchedAt(Long epochSeconds) {
        if (epochSeconds == null) {
            return KstClock.now();
        }
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), KstClock.ZONE);
    }
}
