package com.fxflow.domain.fxrate.scheduler;

import com.fxflow.domain.fxrate.exception.FxRateErrorCode;
import com.fxflow.domain.fxrate.service.FxRateService;
import com.fxflow.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FxRateSchedulerTest {

    @Mock
    private FxRateService fxRateService;

    @InjectMocks
    private FxRateScheduler fxRateScheduler;

    @Test
    @DisplayName("collectFxRate - 수집 서비스에 위임한다")
    void collectFxRate_delegatesToCollect() {
        fxRateScheduler.collectFxRate();

        verify(fxRateService).collectUsdKrwRate();
    }

    @Test
    @DisplayName("collectFxRate - 수집 실패(예외)를 삼키고 전파하지 않는다")
    void collectFxRate_swallowsException() {
        doThrow(new BusinessException(FxRateErrorCode.FX_RATE_FETCH_FAILED))
                .when(fxRateService).collectUsdKrwRate();

        assertThatCode(() -> fxRateScheduler.collectFxRate())
                .doesNotThrowAnyException();

        verify(fxRateService).collectUsdKrwRate();
    }
}
