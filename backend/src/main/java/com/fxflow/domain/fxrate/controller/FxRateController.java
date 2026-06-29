package com.fxflow.domain.fxrate.controller;

import com.fxflow.domain.fxrate.dto.response.FxRateHistoryResponse;
import com.fxflow.domain.fxrate.dto.response.FxRateResponse;
import com.fxflow.domain.fxrate.enums.FxRateHistoryPeriod;
import com.fxflow.domain.fxrate.exception.FxRateErrorCode;
import com.fxflow.domain.fxrate.service.FxRateQueryService;
import com.fxflow.global.exception.BusinessException;
import com.fxflow.global.fx.FxRateSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Locale;

@RestController
@RequestMapping("/api/v1/fxrates")
@RequiredArgsConstructor
public class FxRateController {

    private final FxRateQueryService fxRateQueryService;

    // 최신 매매기준율 + 전일(15:30 기준) 대비 변동 조회 (기본 USD/KRW). 환율 데이터가 없으면 404(F-002).
    @GetMapping("/latest")
    public ResponseEntity<FxRateResponse> getLatestRate(
            @RequestParam(defaultValue = "USD") String base,
            @RequestParam(defaultValue = "KRW") String quote
    ) {
        // 통화코드는 항상 대문자로 저장되므로 입력을 대문자로 정규화
        // (Locale.ROOT: 터키어 등 로케일 의존 대문자 변환 i→İ 회피)
        String baseCode = base.toUpperCase(Locale.ROOT);
        String quoteCode = quote.toUpperCase(Locale.ROOT);

        FxRateSnapshot snapshot = fxRateQueryService.getLatestRate(baseCode, quoteCode)
                .orElseThrow(() -> new BusinessException(FxRateErrorCode.FX_RATE_NOT_FOUND));
        BigDecimal previousRate = fxRateQueryService.getPreviousDayBaselineMid(baseCode, quoteCode).orElse(null);

        return ResponseEntity.ok(FxRateResponse.from(snapshot, previousRate));
    }

    // 환율 이력 조회 (기본 USD/KRW, 1D). period: 1D(1일)/1W(1주일)/1M(1달). 데이터가 없으면 200 + 빈 배열.
    @GetMapping("/history")
    public ResponseEntity<FxRateHistoryResponse> getHistory(
            @RequestParam(defaultValue = "USD") String base,
            @RequestParam(defaultValue = "KRW") String quote,
            @RequestParam(defaultValue = "1D") String period
    ) {
        FxRateHistoryResponse response = fxRateQueryService.getHistory(
                base.toUpperCase(Locale.ROOT),
                quote.toUpperCase(Locale.ROOT),
                FxRateHistoryPeriod.from(period)
        );
        return ResponseEntity.ok(response);
    }
}
