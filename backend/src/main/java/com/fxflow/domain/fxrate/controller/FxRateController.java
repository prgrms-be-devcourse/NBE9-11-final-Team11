package com.fxflow.domain.fxrate.controller;

import com.fxflow.domain.fxrate.dto.response.FxRateResponse;
import com.fxflow.domain.fxrate.exception.FxRateErrorCode;
import com.fxflow.domain.fxrate.service.FxRateQueryService;
import com.fxflow.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

@RestController
@RequestMapping("/api/v1/fxrates")
@RequiredArgsConstructor
public class FxRateController {

    private final FxRateQueryService fxRateQueryService;

    // 최신 매매기준율 조회 (기본 USD/KRW). 환율 데이터가 없으면 404(F-002)를 반환한다.
    @GetMapping("/latest")
    public ResponseEntity<FxRateResponse> getLatestRate(
            @RequestParam(defaultValue = "USD") String base,
            @RequestParam(defaultValue = "KRW") String quote
    ) {
        // 통화코드는 항상 대문자로 저장되므로 입력을 대문자로 정규화
        // (Locale.ROOT: 터키어 등 로케일 의존 대문자 변환 i→İ 회피)
        FxRateResponse response = fxRateQueryService
                .getLatestRate(base.toUpperCase(Locale.ROOT), quote.toUpperCase(Locale.ROOT))
                .map(FxRateResponse::from)
                .orElseThrow(() -> new BusinessException(FxRateErrorCode.FX_RATE_NOT_FOUND));
        return ResponseEntity.ok(response);
    }
}
