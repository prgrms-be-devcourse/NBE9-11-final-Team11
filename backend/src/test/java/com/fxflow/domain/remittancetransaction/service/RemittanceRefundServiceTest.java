package com.fxflow.domain.remittancetransaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fxflow.domain.companypool.errorcode.PoolErrorCode;
import com.fxflow.domain.companypool.service.CompanyPoolService;
import com.fxflow.domain.mockbankaccount.service.MockBankAccountService;
import com.fxflow.domain.remittancetransaction.entity.RemittanceTransaction;
import com.fxflow.domain.remittancetransaction.enums.RemittanceMethod;
import com.fxflow.domain.remittancetransaction.enums.RemittanceReason;
import com.fxflow.domain.remittancetransaction.enums.TransferStatus;
import com.fxflow.domain.remittancetransaction.repository.RemittanceTransactionRepository;
import com.fxflow.domain.user.entity.User;
import com.fxflow.domain.userlimitusage.entity.UserAnnualUsage;
import com.fxflow.domain.userlimitusage.repository.UserAnnualUsageRepository;
import com.fxflow.global.exception.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RemittanceRefundServiceTest {

    @Mock
    private RemittanceTransactionRepository remittanceTransactionRepository;

    @Mock
    private CompanyPoolService companyPoolService;

    @Mock
    private MockBankAccountService mockBankAccountService;

    @Mock
    private UserAnnualUsageRepository userAnnualUsageRepository;

    @InjectMocks
    private RemittanceRefundService remittanceRefundService;

    @Test
    @DisplayName("실패 환불: 송금자 모의계좌로 원화 입금액을 환불하고 송금을 실패 처리한다")
    void refundAfterPayoutFailure_refundsKrw() {
        // given
        Long transferId = 10L;
        Long sourceMockAccountId = 30L;
        RemittanceTransaction remittanceTransaction = createFundedTransaction(transferId);
        UserAnnualUsage annualUsage = createAnnualUsage(remittanceTransaction.getUserId());
        BusinessException exception = new BusinessException(PoolErrorCode.POOL_INSUFFICIENT_BALANCE);
        int currentYear = LocalDate.now(ZoneId.of("Asia/Seoul")).getYear();

        when(remittanceTransactionRepository.findByIdForUpdate(transferId))
                .thenReturn(Optional.of(remittanceTransaction));
        when(userAnnualUsageRepository.findByUserIdAndYearForUpdate(remittanceTransaction.getUserId(), currentYear))
                .thenReturn(Optional.of(annualUsage));

        // when
        remittanceRefundService.refundAfterPayoutFailure(transferId, exception);

        // then
        verify(companyPoolService).withdrawForRemittance(
                anyString(),
                eq("KRW"),
                eq(new BigDecimal("1008000.00")),
                anyString()
        );
        verify(mockBankAccountService).refundForRemittance(
                anyString(),
                eq(sourceMockAccountId),
                eq(new BigDecimal("1008000.00")),
                eq("KRW"),
                anyString()
        );
        assertThat(annualUsage.getAnnualUsedUsd()).isEqualByComparingTo(new BigDecimal("263.48"));
        assertThat(remittanceTransaction.getStatus()).isEqualTo(TransferStatus.FAILED);
        assertThat(remittanceTransaction.getFailureReason())
                .isEqualTo(PoolErrorCode.POOL_INSUFFICIENT_BALANCE.getMessage());
    }

    @Test
    @DisplayName("환불 실패: 운영자 확인이 필요한 환불 실패 상태로 기록한다")
    void markRefundFailed_marksRefundFailed() {
        // given
        Long transferId = 10L;
        RemittanceTransaction remittanceTransaction = createFundedTransaction(transferId);
        UserAnnualUsage annualUsage = createAnnualUsage(remittanceTransaction.getUserId());
        RuntimeException exception = new RuntimeException("환불 처리 실패");
        int currentYear = LocalDate.now(ZoneId.of("Asia/Seoul")).getYear();

        when(remittanceTransactionRepository.findByIdForUpdate(transferId))
                .thenReturn(Optional.of(remittanceTransaction));
        when(userAnnualUsageRepository.findByUserIdAndYearForUpdate(remittanceTransaction.getUserId(), currentYear))
                .thenReturn(Optional.of(annualUsage));

        // when
        remittanceRefundService.markRefundFailed(transferId, exception);

        // then
        assertThat(annualUsage.getAnnualUsedUsd()).isEqualByComparingTo(new BigDecimal("263.48"));
        assertThat(remittanceTransaction.getStatus()).isEqualTo(TransferStatus.REFUND_FAILED);
        assertThat(remittanceTransaction.getFailureReason()).isEqualTo("환불 처리 실패");
    }

    private RemittanceTransaction createFundedTransaction(Long transferId) {
        RemittanceTransaction remittanceTransaction = RemittanceTransaction.create(
                1L,
                1L,
                null,
                RemittanceMethod.BANK_TRANSFER.name(),
                null,
                null,
                "KRW",
                new BigDecimal("1000000.00"),
                "USD",
                new BigDecimal("736.52"),
                new BigDecimal("1351.00000000"),
                new BigDecimal("8000.00"),
                new BigDecimal("1000000.00"),
                new BigDecimal("736.52"),
                RemittanceReason.LIVING_EXPENSES.name(),
                "생활비 송금",
                "idempotency-key",
                "JNL-TEST-REFUND"
        );
        ReflectionTestUtils.setField(remittanceTransaction, "id", transferId);
        remittanceTransaction.fund(30L);

        return remittanceTransaction;
    }

    private UserAnnualUsage createAnnualUsage(Long userId) {
        User user = User.create(
                "remittance-user@example.com",
                "encoded-password",
                "송금사용자"
        );
        ReflectionTestUtils.setField(user, "id", userId);

        UserAnnualUsage usage = UserAnnualUsage.create(user, LocalDate.now(ZoneId.of("Asia/Seoul")).getYear());
        usage.addUsage(new BigDecimal("1000.00"));

        return usage;
    }
}
