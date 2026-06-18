package com.fxflow.domain.fxrate.scheduler;

import com.fxflow.domain.fxrate.service.FxRateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
// 테스트/CI 환경에서 외부 API 호출을 막기 위해 프로퍼티로 on/off (미설정 시 기본 활성)
@ConditionalOnProperty(name = "fxrate.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class FxRateScheduler {

    private final FxRateService fxRateService;

    // 기동 직후부터 fixed-delay 간격으로 USD/KRW 환율을 수집 (주기는 application.yaml에서 외부화, 기본 2분)
    @Scheduled(fixedDelayString = "${fxrate.scheduler.fixed-delay-ms:120000}")
    public void collectFxRate() {
        try {
            fxRateService.collectUsdKrwRate();
        } catch (Exception e) {
            // 1회 수집 실패가 다음 주기를 막지 않도록 삼키고 경고문만 생성 (DB는 마지막 성공값을 그대로 유지)
            log.warn("환율 수집 스케줄 실행 실패. 다음 주기에 재시도합니다", e);
        }
    }
}
