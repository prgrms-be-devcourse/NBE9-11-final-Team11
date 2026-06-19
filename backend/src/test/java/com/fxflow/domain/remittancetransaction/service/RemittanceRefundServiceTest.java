package com.fxflow.domain.remittancetransaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fxflow.domain.companypool.errorcode.PoolErrorCode;
import com.fxflow.domain.companypool.service.CompanyPoolService;
import com.fxflow.domain.mockbankaccount.service.MockBankAccountService;
import com.fxflow.domain.remittancetransaction.entity.RemittanceTransaction;
import com.fxflow.domain.remittancetransaction.enums.RemittanceReason;
import com.fxflow.domain.remittancetransaction.enums.TransferStatus;
import com.fxflow.domain.remittancetransaction.repository.RemittanceTransactionRepository;
import com.fxflow.global.exception.BusinessException;
import java.math.BigDecimal;
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

    @InjectMocks
    private RemittanceRefundService remittanceRefundService;

    @Test
    @DisplayName("실패 환불: 송금자 모의계좌로 원화 입금액을 환불하고 송금을 실패 처리한다")
    void refundAfterPayoutFailure_refundsKrw() {
        // given
        Long transferId = 10L;
        Long sourceMockAccountId = 30L;
        RemittanceTransaction remittanceTransaction = createFundedTransaction(transferId);
        BusinessException exception = new BusinessException(PoolErrorCode.POOL_INSUFFICIENT_BALANCE);

        when(remittanceTransactionRepository.findById(transferId))
                .thenReturn(Optional.of(remittanceTransaction));

        // when
        remittanceRefundService.refundAfterPayoutFailure(transferId, exception);

        // then
        verify(companyPoolService).withdrawForRemittance(
                "JRN-TRF-" + transferId + "-REFUND",
                "KRW",
                new BigDecimal("1008000.00"),
                transferId
        );
        verify(mockBankAccountService).refundForRemittance(
                "JRN-TRF-" + transferId + "-REFUND",
                sourceMockAccountId,
                new BigDecimal("1008000.00"),
                "KRW",
                String.valueOf(transferId)
        );
        assertThat(remittanceTransaction.getStatus()).isEqualTo(TransferStatus.FAILED);
        assertThat(remittanceTransaction.getFailureReason())
                .isEqualTo(PoolErrorCode.POOL_INSUFFICIENT_BALANCE.getMessage());
    }

    private RemittanceTransaction createFundedTransaction(Long transferId) {
        RemittanceTransaction remittanceTransaction = RemittanceTransaction.create(
                1L,
                1L,
                null,
                "BANK_TRANSFER",
                null,
                null,
                null,
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
                "idempotency-key"
        );
        ReflectionTestUtils.setField(remittanceTransaction, "id", transferId);
        remittanceTransaction.fund(30L);

        return remittanceTransaction;
    }
}
