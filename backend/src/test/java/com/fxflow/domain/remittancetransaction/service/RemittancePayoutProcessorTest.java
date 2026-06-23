package com.fxflow.domain.remittancetransaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fxflow.domain.companypool.service.CompanyPoolService;
import com.fxflow.domain.mockbankaccount.service.MockBankAccountService;
import com.fxflow.domain.remittancetransaction.entity.Recipient;
import com.fxflow.domain.remittancetransaction.entity.RemittanceTransaction;
import com.fxflow.domain.remittancetransaction.enums.RemittanceMethod;
import com.fxflow.domain.remittancetransaction.enums.RemittanceReason;
import com.fxflow.domain.remittancetransaction.enums.TransferStatus;
import com.fxflow.domain.remittancetransaction.repository.RecipientRepository;
import com.fxflow.domain.remittancetransaction.repository.RemittanceTransactionRepository;
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
class RemittancePayoutProcessorTest {

    @Mock
    private RemittanceTransactionRepository remittanceTransactionRepository;

    @Mock
    private RecipientRepository recipientRepository;

    @Mock
    private CompanyPoolService companyPoolService;

    @Mock
    private MockBankAccountService mockBankAccountService;

    @InjectMocks
    private RemittancePayoutProcessor remittancePayoutProcessor;

    @Test
    @DisplayName("성공: 입금 완료된 송금 건은 외화풀 차감 후 수취인 모의계좌에 입금하고 완료 처리한다")
    void process_success() {
        // given
        Long transferId = 10L;
        Long targetMockAccountId = 40L;
        RemittanceTransaction remittanceTransaction = createFundedTransaction(transferId);
        Recipient recipient = createRecipient();

        when(remittanceTransactionRepository.findByIdForUpdate(transferId))
                .thenReturn(Optional.of(remittanceTransaction));
        when(recipientRepository.findById(remittanceTransaction.getRecipientId()))
                .thenReturn(Optional.of(recipient));
        when(mockBankAccountService.depositForRemittance(
                anyString(),
                eq("1234567890"),
                eq(new BigDecimal("736.52")),
                eq("USD"),
                anyString()
        )).thenReturn(targetMockAccountId);

        // when
        remittancePayoutProcessor.process(transferId);

        // then
        verify(companyPoolService).withdrawForRemittance(
                anyString(),
                eq("USD"),
                eq(new BigDecimal("736.52")),
                anyString()
        );
        verify(mockBankAccountService).depositForRemittance(
                anyString(),
                eq("1234567890"),
                eq(new BigDecimal("736.52")),
                eq("USD"),
                anyString()
        );
        assertThat(remittanceTransaction.getStatus()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(remittanceTransaction.getTargetMockAccountId()).isEqualTo(targetMockAccountId);
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
                "JNL-TEST-PAYOUT"
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
