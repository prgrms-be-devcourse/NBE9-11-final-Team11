package com.fxflow.domain.remittancetransaction.service;

import com.fxflow.domain.companypool.errorcode.PoolErrorCode;
import com.fxflow.domain.companypool.service.CompanyPoolService;
import com.fxflow.domain.mockbankaccount.service.MockBankAccountService;
import com.fxflow.domain.remittancetransaction.entity.Recipient;
import com.fxflow.domain.remittancetransaction.entity.RemittanceTransaction;
import com.fxflow.domain.remittancetransaction.enums.RemittanceReason;
import com.fxflow.domain.remittancetransaction.enums.TransferStatus;
import com.fxflow.domain.remittancetransaction.repository.RecipientRepository;
import com.fxflow.domain.remittancetransaction.repository.RemittanceTransactionRepository;
import com.fxflow.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RemittancePayoutServiceTest {

    @Mock
    private RemittanceTransactionRepository remittanceTransactionRepository;

    @Mock
    private RecipientRepository recipientRepository;

    @Mock
    private CompanyPoolService companyPoolService;

    @Mock
    private MockBankAccountService mockBankAccountService;

    @InjectMocks
    private RemittancePayoutService remittancePayoutService;

    @Test
    @DisplayName("성공: 입금 완료된 송금 건은 외화풀 차감 후 수취인 모의계좌에 입금하고 완료 처리한다")
    void processPayout_success() {
        // given
        Long transferId = 10L;
        Long targetMockAccountId = 40L;
        RemittanceTransaction remittanceTransaction = createFundedTransaction(transferId);
        Recipient recipient = createRecipient();

        when(remittanceTransactionRepository.findById(transferId))
                .thenReturn(Optional.of(remittanceTransaction));
        when(recipientRepository.findById(remittanceTransaction.getRecipientId()))
                .thenReturn(Optional.of(recipient));
        when(mockBankAccountService.depositForRemittance(
                "JRN-TRF-" + transferId + "-PAYOUT",
                "1234567890",
                new BigDecimal("736.52"),
                "USD",
                String.valueOf(transferId)
        )).thenReturn(targetMockAccountId);

        // when
        remittancePayoutService.processPayout(transferId);

        // then
        verify(companyPoolService).withdrawForRemittance(
                "JRN-TRF-" + transferId + "-PAYOUT",
                "USD",
                new BigDecimal("736.52"),
                transferId
        );
        verify(mockBankAccountService).depositForRemittance(
                "JRN-TRF-" + transferId + "-PAYOUT",
                "1234567890",
                new BigDecimal("736.52"),
                "USD",
                String.valueOf(transferId)
        );
        assertThat(remittanceTransaction.getStatus()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(remittanceTransaction.getTargetMockAccountId()).isEqualTo(targetMockAccountId);
    }

    @Test
    @DisplayName("실패: 외화풀 차감 실패 시 송금을 실패 처리하고 송금자 모의계좌로 원화 입금액을 환불한다")
    void processPayout_fail_refundsKrw() {
        // given
        Long transferId = 10L;
        Long sourceMockAccountId = 30L;
        RemittanceTransaction remittanceTransaction = createFundedTransaction(transferId);
        Recipient recipient = createRecipient();
        BusinessException exception = new BusinessException(PoolErrorCode.POOL_INSUFFICIENT_BALANCE);

        when(remittanceTransactionRepository.findById(transferId))
                .thenReturn(Optional.of(remittanceTransaction));
        when(recipientRepository.findById(remittanceTransaction.getRecipientId()))
                .thenReturn(Optional.of(recipient));
        doThrow(exception).when(companyPoolService).withdrawForRemittance(
                "JRN-TRF-" + transferId + "-PAYOUT",
                "USD",
                new BigDecimal("736.52"),
                transferId
        );

        // when
        remittancePayoutService.processPayout(transferId);

        // then
        verify(mockBankAccountService, never()).depositForRemittance(
                "JRN-TRF-" + transferId + "-PAYOUT",
                "1234567890",
                new BigDecimal("736.52"),
                "USD",
                String.valueOf(transferId)
        );
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

    private Recipient createRecipient() {
        Recipient recipient = Recipient.create(
                1L,
                "John Doe",
                "US",
                "USD",
                "Chase Bank",
                "1234567890"
        );
        ReflectionTestUtils.setField(recipient, "id", 1L);

        return recipient;
    }
}