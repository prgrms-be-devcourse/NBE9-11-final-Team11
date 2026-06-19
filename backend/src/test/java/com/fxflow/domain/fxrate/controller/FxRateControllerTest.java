package com.fxflow.domain.fxrate.controller;

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

    @Test
    @DisplayName("최신 환율 조회 성공 - 200과 매매기준율/갱신시각을 반환한다")
    void getLatestRate_success() throws Exception {
        FxRateSnapshot snapshot = new FxRateSnapshot(
                "USD", "KRW",
                new BigDecimal("1350"),
                new BigDecimal("0.01"),
                LocalDateTime.of(2026, 6, 18, 12, 0, 0)
        );
        when(fxRateQueryService.getLatestRate("USD", "KRW")).thenReturn(Optional.of(snapshot));

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
        FxRateSnapshot snapshot = new FxRateSnapshot(
                "USD", "KRW",
                new BigDecimal("1350"),
                new BigDecimal("0.01"),
                LocalDateTime.of(2026, 6, 18, 12, 0, 0)
        );
        // 컨트롤러가 대문자로 정규화해야 서비스가 "USD"/"KRW"로 호출된다 (정규화 없으면 stub 미스 → 404)
        when(fxRateQueryService.getLatestRate("USD", "KRW")).thenReturn(Optional.of(snapshot));

        mockMvc.perform(get("/api/v1/fxrates/latest").param("base", "usd").param("quote", "krw"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseCurrency").value("USD"))
                .andExpect(jsonPath("$.quoteCurrency").value("KRW"));
    }
}
