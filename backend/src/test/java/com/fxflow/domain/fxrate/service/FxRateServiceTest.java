package com.fxflow.domain.fxrate.service;

import com.fxflow.domain.fxrate.entity.FxRate;
import com.fxflow.domain.fxrate.exception.FxRateErrorCode;
import com.fxflow.domain.fxrate.repository.FxRateRepository;
import com.fxflow.global.exception.BusinessException;
import com.fxflow.global.fx.FxRateSnapshot;
import com.fxflow.global.fx.FxRateUpdatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(MockitoExtension.class)
@DisplayName("FxRateService - Twelve Data 환율 수집/조회")
class FxRateServiceTest {

    @Mock
    private FxRateRepository fxRateRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private FxRateService fxRateService;
    private MockRestServiceServer mockServer;

    // 엔티티 DEFAULT_SPREAD(=0.01)와 동일한 기대값 (스프레드 검증 기준)
    private static final BigDecimal EXPECTED_SPREAD = new BigDecimal("0");

    @BeforeEach
    void setUp() {
        // FxRateService가 RestClient.Builder를 주입받는 구조라,
        // MockRestServiceServer에 바인딩한 빌더를 그대로 생성자에 전달해 외부 호출 없이 검증한다.
        // (요청 URL/응답 매핑까지 실제 직렬화 경로로 검증)
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();

        fxRateService = new FxRateService(fxRateRepository, eventPublisher, builder, "test-api-key");
    }

    @Nested
    @DisplayName("collectUsdKrwRate - 수집 → 저장 → 이벤트 발행")
    class CollectUsdKrwRate {

        @Test
        @DisplayName("성공: 응답 환율을 mid로 저장(스프레드 기본 적용)하고 갱신 이벤트를 발행한다")
        void success() {
            // given - Twelve Data가 정상 JSON 응답을 반환
            long epochSeconds = 1_718_600_000L;
            String body = """
                    {"symbol":"USD/KRW","rate":1386.50,"timestamp":%d}
                    """.formatted(epochSeconds);
            mockServer.expect(requestTo(containsString("exchange_rate")))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

            // when
            fxRateService.collectUsdKrwRate();

            // then - 저장된 FxRate 검증
            ArgumentCaptor<FxRate> savedCaptor = ArgumentCaptor.forClass(FxRate.class);
            verify(fxRateRepository).save(savedCaptor.capture());
            FxRate saved = savedCaptor.getValue();
            assertThat(saved.getBaseCurrency()).isEqualTo("USD");
            assertThat(saved.getQuoteCurrency()).isEqualTo("KRW");
            assertThat(saved.getMidRate()).isEqualByComparingTo("1386.50");
            assertThat(saved.getSpread()).isEqualByComparingTo(EXPECTED_SPREAD); // 엔티티가 강제하는 기본 스프레드
            assertThat(saved.getSource()).isEqualTo("TwelveData");
            // epoch(seconds) → LocalDateTime 변환 검증 (시스템 타임존 기준)
            assertThat(saved.getFetchedAt())
                    .isEqualTo(LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneId.systemDefault()));

            // then - 발행된 이벤트의 스냅샷 검증 (mid/spread + 파생 매수/매도가)
            ArgumentCaptor<FxRateUpdatedEvent> eventCaptor = ArgumentCaptor.forClass(FxRateUpdatedEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            FxRateSnapshot snapshot = eventCaptor.getValue().snapshot();
            assertThat(snapshot.midRate()).isEqualByComparingTo("1386.50");
            assertThat(snapshot.spread()).isEqualByComparingTo(EXPECTED_SPREAD);
            assertThat(snapshot.buyRate())  // mid × (1 + spread)
                    .isEqualByComparingTo(new BigDecimal("1386.50").multiply(new BigDecimal("1")));
            assertThat(snapshot.sellRate()) // mid × (1 − spread)
                    .isEqualByComparingTo(new BigDecimal("1386.50").multiply(new BigDecimal("1")));

            mockServer.verify();
        }

        @Test
        @DisplayName("실패: 외부 API 오류(5xx)면 FX_RATE_FETCH_FAILED, 저장·이벤트 없음")
        void apiError() {
            // given - 외부 API 5xx
            mockServer.expect(requestTo(containsString("exchange_rate")))
                    .andRespond(withServerError());

            // when & then
            assertThatThrownBy(() -> fxRateService.collectUsdKrwRate())
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(FxRateErrorCode.FX_RATE_FETCH_FAILED);

            verify(fxRateRepository, never()).save(any());
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("실패: 응답 rate가 null이면 FX_RATE_FETCH_FAILED, 저장·이벤트 없음")
        void nullRate() {
            // given - 200 OK지만 rate 누락
            String body = """
                    {"symbol":"USD/KRW","rate":null,"timestamp":1718600000}
                    """;
            mockServer.expect(requestTo(containsString("exchange_rate")))
                    .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

            // when & then
            assertThatThrownBy(() -> fxRateService.collectUsdKrwRate())
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(FxRateErrorCode.FX_RATE_FETCH_FAILED);

            verify(fxRateRepository, never()).save(any());
            verify(eventPublisher, never()).publishEvent(any());
        }
    }

    @Nested
    @DisplayName("getRate - mid 환율 단순 조회 (평가/표시용)")
    class GetRate {

        @Test
        @DisplayName("같은 통화면 환율 1을 반환한다")
        void sameCurrency() {
            assertThat(fxRateService.getRate("KRW", "KRW")).isEqualByComparingTo(BigDecimal.ONE);
        }

        @Test
        @DisplayName("최신 환율이 있으면 mid 환율을 반환한다 (스프레드 미반영)")
        void found() {
            // given
            FxRate fxRate = FxRate.create("USD", "KRW", new BigDecimal("1386.50"), "TwelveData", LocalDateTime.now());
            given(fxRateRepository.findFirstByBaseCurrencyAndQuoteCurrencyOrderByFetchedAtDesc("USD", "KRW"))
                    .willReturn(Optional.of(fxRate));

            // when & then
            assertThat(fxRateService.getRate("USD", "KRW")).isEqualByComparingTo("1386.50");
        }

        @Test
        @DisplayName("통화 코드가 null이면 IllegalArgumentException")
        void nullArgs() {
            assertThatThrownBy(() -> fxRateService.getRate(null, "KRW"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("저장된 환율이 없으면 FX_RATE_NOT_FOUND 비즈니스 예외")
        void notFound() {
            // given
            given(fxRateRepository.findFirstByBaseCurrencyAndQuoteCurrencyOrderByFetchedAtDesc("USD", "KRW"))
                    .willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> fxRateService.getRate("USD", "KRW"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(FxRateErrorCode.FX_RATE_NOT_FOUND);
        }
    }
}
