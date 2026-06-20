package com.fxflow.domain.remittancetransaction.service;

import com.fxflow.domain.companypool.errorcode.PoolErrorCode;
import com.fxflow.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RemittancePayoutServiceTest {

    @Mock
    private RemittancePayoutProcessor remittancePayoutProcessor;

    @Mock
    private RemittanceRefundService remittanceRefundService;

    @InjectMocks
    private RemittancePayoutService remittancePayoutService;

    @Test
    @DisplayName("성공: 후속 지급 처리를 정상 지급 트랜잭션에 위임한다")
    void processPayout_success_delegatesProcessor() {
        // given
        Long transferId = 10L;

        // when
        remittancePayoutService.processPayout(transferId);

        // then
        verify(remittancePayoutProcessor).process(transferId);
    }

    @Test
    @DisplayName("실패: 정상 지급 트랜잭션 실패 시 별도 환불 트랜잭션을 호출한다")
    void processPayout_fail_delegatesRefund() {
        // given
        Long transferId = 10L;
        BusinessException exception = new BusinessException(PoolErrorCode.POOL_INSUFFICIENT_BALANCE);

        doThrow(exception).when(remittancePayoutProcessor).process(transferId);

        // when
        remittancePayoutService.processPayout(transferId);

        // then
        verify(remittanceRefundService).refundAfterPayoutFailure(transferId, exception);
    }

    @Test
    @DisplayName("실패: 환불 트랜잭션까지 실패하면 환불 실패 상태 기록을 호출한다")
    void processPayout_refundFail_marksRefundFailed() {
        // given
        Long transferId = 10L;
        BusinessException payoutException = new BusinessException(PoolErrorCode.POOL_INSUFFICIENT_BALANCE);
        RuntimeException refundException = new RuntimeException("환불 처리 실패");

        doThrow(payoutException).when(remittancePayoutProcessor).process(transferId);
        doThrow(refundException).when(remittanceRefundService)
                .refundAfterPayoutFailure(transferId, payoutException);

        // when
        remittancePayoutService.processPayout(transferId);

        // then
        verify(remittanceRefundService).markRefundFailed(transferId, refundException);
    }
}
