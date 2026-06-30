package com.fxflow.domain.fxrate.controller;

import com.fxflow.domain.fxrate.dto.response.FxRateHistoryResponse;
import com.fxflow.domain.fxrate.enums.FxRateHistoryPeriod;
import com.fxflow.domain.fxrate.service.FxRateQueryService;
import com.fxflow.global.exception.GlobalExceptionHandler;
import com.fxflow.global.fx.FxRateSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FxRateControllerTest {

    private final FxRateQueryService fxRateQueryService = mock(FxRateQueryService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // 환율 조회는 permitAll 경로 — 보안/컨텍스트 없이 컨트롤러만 단독 검증한다.
        mockMvc = MockMvcBuilders.standaloneSetup(new FxRateController(fxRateQueryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private FxRateSnapshot snapshot(String mid) {
        return new FxRateSnapshot(
                "USD", "KRW",
                new BigDecimal(mid),
                new BigDecimal("0.01"),
                LocalDateTime.of(2026, 6, 18, 12, 0, 0)
        );
    }

    @Test
    @DisplayName("최신 환율 조회 성공 - 200과 매매기준율/갱신시각을 반환한다")
    void getLatestRate_success() throws Exception {
        when(fxRateQueryService.getLatestRate("USD", "KRW")).thenReturn(Optional.of(snapshot("1350")));

        mockMvc.perform(get("/api/v1/fxrates/latest").param("base", "USD").param("quote", "KRW"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseCurrency").value("USD"))
                .andExpect(jsonPath("$.quoteCurrency").value("KRW"))
                .andExpect(jsonPath("$.midRate").value(1350.0))
                .andExpect(jsonPath("$.buyRate").value(1363.5))
                .andExpect(jsonPath("$.sellRate").value(1336.5))
                .andExpect(jsonPath("$.fetchedAt").exists());
    }

    @Test
    @DisplayName("전일 기준값이 있으면 전일 대비 변동액/변동률을 함께 반환한다")
    void getLatestRate_withDayOverDayChange() throws Exception {
        when(fxRateQueryService.getLatestRate("USD", "KRW")).thenReturn(Optional.of(snapshot("1320")));
        // 전일 15:30 기준 1300 → 변동액 +20, 변동률 = 20/1300×100 = 1.538.. → 1.54
        when(fxRateQueryService.getPreviousDayBaselineMid("USD", "KRW"))
                .thenReturn(Optional.of(new BigDecimal("1300")));

        mockMvc.perform(get("/api/v1/fxrates/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previousRate").value(1300.0))
                .andExpect(jsonPath("$.changeRate").value(20.0))
                .andExpect(jsonPath("$.changePercent").value(1.54));
    }

    @Test
    @DisplayName("전일 기준값이 없으면 변동 필드는 null로 반환한다")
    void getLatestRate_withoutBaseline() throws Exception {
        when(fxRateQueryService.getLatestRate("USD", "KRW")).thenReturn(Optional.of(snapshot("1320")));
        when(fxRateQueryService.getPreviousDayBaselineMid("USD", "KRW")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/fxrates/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previousRate").doesNotExist())
                .andExpect(jsonPath("$.changeRate").doesNotExist())
                .andExpect(jsonPath("$.changePercent").doesNotExist());
    }

    @Test
    @DisplayName("최신 환율 미존재 - 404와 F-002를 반환한다")
    void getLatestRate_notFound() throws Exception {
        when(fxRateQueryService.getLatestRate("USD", "KRW")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/fxrates/latest"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("F-002"));
    }

    @Test
    @DisplayName("소문자 통화코드 요청도 대문자로 정규화되어 조회된다")
    void getLatestRate_normalizesLowerCase() throws Exception {
        when(fxRateQueryService.getLatestRate("USD", "KRW")).thenReturn(Optional.of(snapshot("1350")));

        mockMvc.perform(get("/api/v1/fxrates/latest").param("base", "usd").param("quote", "krw"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseCurrency").value("USD"))
                .andExpect(jsonPath("$.quoteCurrency").value("KRW"));
    }

    @Test
    @DisplayName("이력 조회 성공 - 200과 기간/시계열을 반환한다")
    void getHistory_success() throws Exception {
        FxRateHistoryResponse history = new FxRateHistoryResponse(
                "USD", "KRW", "1D",
                List.of(
                        new FxRateHistoryResponse.Point(LocalDateTime.of(2026, 6, 18, 9, 0), new BigDecimal("1300.00000000")),
                        new FxRateHistoryResponse.Point(LocalDateTime.of(2026, 6, 18, 10, 0), new BigDecimal("1310.00000000"))
                )
        );
        when(fxRateQueryService.getHistory("USD", "KRW", FxRateHistoryPeriod.ONE_DAY)).thenReturn(history);

        mockMvc.perform(get("/api/v1/fxrates/history").param("period", "1D"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("1D"))
                .andExpect(jsonPath("$.points.length()").value(2))
                .andExpect(jsonPath("$.points[0].midRate").value(1300.0));
    }

    @Test
    @DisplayName("이력 조회 - 데이터가 없으면 200과 빈 배열을 반환한다")
    void getHistory_empty() throws Exception {
        FxRateHistoryResponse empty = new FxRateHistoryResponse("USD", "KRW", "1W", List.of());
        when(fxRateQueryService.getHistory("USD", "KRW", FxRateHistoryPeriod.ONE_WEEK)).thenReturn(empty);

        mockMvc.perform(get("/api/v1/fxrates/history").param("period", "1W"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points.length()").value(0));
    }

    @Test
    @DisplayName("이력 조회 - 유효하지 않은 기간이면 400과 F-003을 반환한다")
    void getHistory_invalidPeriod() throws Exception {
        mockMvc.perform(get("/api/v1/fxrates/history").param("period", "3Y"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("F-003"));
    }
}
