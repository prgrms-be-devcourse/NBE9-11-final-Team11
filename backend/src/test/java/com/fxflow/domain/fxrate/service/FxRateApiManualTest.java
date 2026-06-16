package com.fxflow.domain.fxrate.service;

import com.fxflow.domain.fxrate.entity.FxRate;
import com.fxflow.domain.fxrate.repository.FxRateRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * [수동 검증용] 실제 Twelve Data API를 1회 호출해 USD/KRW 환율 수집이 동작하는지 확인한다.
 *
 * 평소 ./gradlew test / CI 에서는 환경변수 TWELVEDATA_API_KEY 가 없으면 자동 스킵된다.
 * (무료 API 쿼터·외부 호출을 평상시엔 건드리지 않기 위함)
 *
 * 실행 방법:
 *   - IntelliJ : Run Configuration → Environment variables 에 TWELVEDATA_API_KEY=발급키 추가 후 실행
 *   - 터미널   : TWELVEDATA_API_KEY=발급키 ./gradlew test --tests "*FxRateApiManualTest"
 *
 * Spring 컨텍스트/DB를 띄우지 않고(가벼운 버전), repository/publisher는 mock으로 두어
 * "외부 호출 → 응답 매핑 → 저장 호출"까지의 실제 연동만 검증한다.
 * 키는 yaml이 아니라 위 환경변수(System.getenv)에서 읽는다.
 */
@EnabledIfEnvironmentVariable(named = "TWELVEDATA_API_KEY", matches = ".+")
@DisplayName("[수동] Twelve Data 실제 USD/KRW 환율 수집 검증")
class FxRateApiManualTest {

    @Test
    @DisplayName("실제 API를 호출해 mid 환율을 받아 저장 인자로 전달한다")
    void collectRealUsdKrwRate() {
        // given - DB 없이 외부 호출만 검증 (repository/publisher는 mock)
        FxRateRepository fxRateRepository = mock(FxRateRepository.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        String apiKey = System.getenv("TWELVEDATA_API_KEY");
        FxRateService fxRateService = new FxRateService(fxRateRepository, eventPublisher, apiKey);

        // when - 실제 Twelve Data 호출
        fxRateService.collectUsdKrwRate();

        // then - 저장 호출 인자(FxRate)를 캡처해 실제 받은 값을 확인
        ArgumentCaptor<FxRate> captor = ArgumentCaptor.forClass(FxRate.class);
        verify(fxRateRepository).save(captor.capture());
        FxRate collected = captor.getValue();

        assertThat(collected.getBaseCurrency()).isEqualTo("USD");
        assertThat(collected.getQuoteCurrency()).isEqualTo("KRW");
        assertThat(collected.getMidRate()).isPositive(); // 실제 환율은 양수여야 한다
        System.out.println(">>> 실제 USD/KRW mid rate = " + collected.getMidRate()
                + " (fetchedAt=" + collected.getFetchedAt() + ")");
    }
}
