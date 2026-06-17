package com.fxflow.domain.remittancetransaction.service;

import com.fxflow.domain.companypool.service.CompanyPoolService;
import com.fxflow.domain.mockbankaccount.service.MockBankAccountService;
import com.fxflow.domain.remittancetransaction.dto.request.RemittanceTransactionCreateRequest;
import com.fxflow.domain.remittancetransaction.dto.response.RemittanceQuoteSnapshot;
import com.fxflow.domain.remittancetransaction.dto.response.RemittanceMockFundedResponse;
import com.fxflow.domain.remittancetransaction.dto.response.RemittanceTransactionCreateResponse;
import com.fxflow.domain.remittancetransaction.entity.RemittanceTransaction;
import com.fxflow.domain.remittancetransaction.entity.VirtualAccount;
import com.fxflow.domain.remittancetransaction.enums.RemittanceReason;
import com.fxflow.domain.remittancetransaction.enums.TransferStatus;
import com.fxflow.domain.remittancetransaction.enums.VirtualAccountStatus;
import com.fxflow.domain.remittancetransaction.errorcode.RecipientErrorCode;
import com.fxflow.domain.remittancetransaction.errorcode.RemittanceTransactionErrorCode;
import com.fxflow.domain.remittancetransaction.repository.RecipientRepository;
import com.fxflow.domain.remittancetransaction.repository.RemittanceTransactionRepository;
import com.fxflow.domain.remittancetransaction.repository.VirtualAccountRepository;
import com.fxflow.domain.remittancetransaction.validator.RemittanceValidator;
import com.fxflow.domain.transactionlimit.errorcode.TransactionLimitErrorCode;
import com.fxflow.domain.transactionlimit.repository.TransactionLimitRepository;
import com.fxflow.domain.userlimitusage.repository.UserAnnualUsageRepository;
import com.fxflow.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RemittanceTransactionServiceTest {

    @Mock
    private UserAnnualUsageRepository userAnnualUsageRepository;

    @Mock
    private TransactionLimitRepository transactionLimitRepository;

    @Mock
    private RecipientRepository recipientRepository;

    @Mock
    private RemittanceTransactionRepository remittanceTransactionRepository;

    @Mock
    private VirtualAccountRepository virtualAccountRepository;

    @Mock
    private RemittanceQuoteProvider remittanceQuoteProvider;

    @Mock
    private RemittanceValidator remittanceValidator;

    @Mock
    private MockBankAccountService mockBankAccountService;

    @Mock
    private CompanyPoolService companyPoolService;

    @InjectMocks
    private RemittanceTransactionService remittanceTransactionService;

    @Test
    @DisplayName("성공: 송금 주문을 생성하고 가상계좌를 발급한다")
    void createTransfer_success() {
        // given
        Long userId = 1L;
        Long transferId = 10L;
        Long virtualAccountId = 20L;
        RemittanceTransactionCreateRequest request = createRequest();
        RemittanceQuoteSnapshot quote = createQuote();

        when(remittanceQuoteProvider.getQuote(request.quoteId())).thenReturn(quote);
        when(recipientRepository.existsByIdAndUserIdAndDeletedAtIsNull(quote.recipientId(), userId))
                .thenReturn(true);
        when(remittanceTransactionRepository.save(any(RemittanceTransaction.class)))
                .thenAnswer(invocation -> {
                    RemittanceTransaction remittanceTransaction = invocation.getArgument(0);
                    ReflectionTestUtils.setField(remittanceTransaction, "id", transferId);
                    return remittanceTransaction;
                });
        when(virtualAccountRepository.save(any(VirtualAccount.class)))
                .thenAnswer(invocation -> {
                    VirtualAccount virtualAccount = invocation.getArgument(0);
                    ReflectionTestUtils.setField(virtualAccount, "id", virtualAccountId);
                    return virtualAccount;
                });

        // when
        RemittanceTransactionCreateResponse response =
                remittanceTransactionService.createTransfer(userId, request);

        // then
        assertThat(response.transferId()).isEqualTo(transferId);
        assertThat(response.status()).isEqualTo(TransferStatus.PENDING);
        assertThat(response.virtualAccount().bankName()).isEqualTo("하나은행");
        assertThat(response.virtualAccount().amount()).isEqualTo(new BigDecimal("1008000"));
        assertThat(response.virtualAccount().expiredAt()).isNotNull();

        ArgumentCaptor<RemittanceTransaction> transactionCaptor =
                ArgumentCaptor.forClass(RemittanceTransaction.class);
        verify(remittanceTransactionRepository).save(transactionCaptor.capture());
        RemittanceTransaction savedTransaction = transactionCaptor.getValue();

        assertThat(savedTransaction.getUserId()).isEqualTo(userId);
        assertThat(savedTransaction.getRecipientId()).isEqualTo(quote.recipientId());
        assertThat(savedTransaction.getReason()).isEqualTo(RemittanceReason.LIVING_EXPENSES.name());
        assertThat(savedTransaction.getReasonDetail()).isEqualTo("생활비 송금");
        assertThat(savedTransaction.getStatus()).isEqualTo(TransferStatus.PENDING);
        verify(remittanceValidator).validateLimits(userId, quote.amountUsd());

        ArgumentCaptor<VirtualAccount> virtualAccountCaptor =
                ArgumentCaptor.forClass(VirtualAccount.class);
        verify(virtualAccountRepository).save(virtualAccountCaptor.capture());
        VirtualAccount savedVirtualAccount = virtualAccountCaptor.getValue();

        assertThat(savedVirtualAccount.getUserId()).isEqualTo(userId);
        assertThat(savedVirtualAccount.getRemittanceTransactionId()).isEqualTo(transferId);
        assertThat(savedVirtualAccount.getExpectedAmount()).isEqualByComparingTo(new BigDecimal("1008000.00"));
        assertThat(savedVirtualAccount.getRefType()).isEqualTo("REMITTANCE");
        assertThat(savedVirtualAccount.getRefId()).isEqualTo(String.valueOf(transferId));
    }

    @Test
    @DisplayName("실패: quoteId에 해당하는 송금 견적이 없으면 예외가 발생한다")
    void createTransfer_fail_quoteNotFound() {
        // given
        Long userId = 1L;
        RemittanceTransactionCreateRequest request = createRequest();

        when(remittanceQuoteProvider.getQuote(request.quoteId()))
                .thenThrow(new BusinessException(RemittanceTransactionErrorCode.QUOTE_NOT_FOUND));

        // when & then
        assertThatThrownBy(() -> remittanceTransactionService.createTransfer(userId, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(RemittanceTransactionErrorCode.QUOTE_NOT_FOUND);

        verifyNoInteractions(recipientRepository);
        verifyNoInteractions(remittanceTransactionRepository);
        verifyNoInteractions(virtualAccountRepository);
    }

    @Test
    @DisplayName("실패: 송금 한도를 초과하면 송금 주문과 가상계좌를 생성하지 않는다")
    void createTransfer_fail_limitExceeded() {
        // given
        Long userId = 1L;
        RemittanceTransactionCreateRequest request = createRequest();
        RemittanceQuoteSnapshot quote = createQuote();
        BusinessException exception =
                new BusinessException(TransactionLimitErrorCode.ANNUAL_REMITTANCE_LIMIT_EXCEEDED);

        when(remittanceQuoteProvider.getQuote(request.quoteId())).thenReturn(quote);
        when(recipientRepository.existsByIdAndUserIdAndDeletedAtIsNull(quote.recipientId(), userId))
                .thenReturn(true);
        doThrow(exception).when(remittanceValidator).validateLimits(userId, quote.amountUsd());

        // when & then
        assertThatThrownBy(() -> remittanceTransactionService.createTransfer(userId, request))
                .isSameAs(exception);

        verifyNoInteractions(remittanceTransactionRepository);
        verifyNoInteractions(virtualAccountRepository);
    }

    @Test
    @DisplayName("실패: 선택한 수취인이 로그인한 사용자의 수취인이 아니면 예외가 발생한다")
    void createTransfer_fail_recipientNotFound() {
        // given
        Long userId = 1L;
        RemittanceTransactionCreateRequest request = createRequest();
        RemittanceQuoteSnapshot quote = createQuote();

        when(remittanceQuoteProvider.getQuote(request.quoteId())).thenReturn(quote);
        when(recipientRepository.existsByIdAndUserIdAndDeletedAtIsNull(quote.recipientId(), userId))
                .thenReturn(false);

        // when & then
        assertThatThrownBy(() -> remittanceTransactionService.createTransfer(userId, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(RecipientErrorCode.RECIPIENT_NOT_FOUND);

        verifyNoInteractions(remittanceTransactionRepository);
        verifyNoInteractions(virtualAccountRepository);
    }

    @Test
    @DisplayName("성공: Mock 입금 확인 시 모의계좌를 차감하고 송금 거래를 FUNDED로 변경한다")
    void mockFundTransfer_success() {
        // given
        Long userId = 1L;
        Long transferId = 10L;
        Long sourceMockAccountId = 30L;
        RemittanceTransaction remittanceTransaction = createPendingTransaction(userId, transferId);
        VirtualAccount virtualAccount = createVirtualAccount(userId, transferId, LocalDateTime.now().plusMinutes(30));

        when(remittanceTransactionRepository.findById(transferId))
                .thenReturn(Optional.of(remittanceTransaction));
        when(virtualAccountRepository.findByRemittanceTransactionId(transferId))
                .thenReturn(Optional.of(virtualAccount));
        when(mockBankAccountService.withdrawForRemittance(
                eq(userId),
                eq("JRN-TRF-" + transferId + "-FUND"),
                eq(new BigDecimal("1008000.00")),
                eq("KRW"),
                eq(String.valueOf(transferId))
        )).thenReturn(sourceMockAccountId);

        // when
        RemittanceMockFundedResponse response =
                remittanceTransactionService.mockFundTransfer(userId, transferId);

        // then
        assertThat(response.transferId()).isEqualTo(transferId);
        assertThat(response.status()).isEqualTo(TransferStatus.FUNDED);
        assertThat(response.virtualAccountStatus()).isEqualTo(VirtualAccountStatus.PAID);
        assertThat(response.fundedAt()).isNotNull();
        assertThat(remittanceTransaction.getStatus()).isEqualTo(TransferStatus.FUNDED);
        assertThat(remittanceTransaction.getSourceMockAccountId()).isEqualTo(sourceMockAccountId);
        assertThat(virtualAccount.getStatus()).isEqualTo(VirtualAccountStatus.PAID);
        assertThat(virtualAccount.getPaidAt()).isNotNull();

        verify(companyPoolService).deposit(
                "JRN-TRF-" + transferId + "-FUND",
                "KRW",
                new BigDecimal("1008000.00")
        );
    }

    @Test
    @DisplayName("실패: 송금 거래가 없으면 Mock 입금 확인을 처리하지 않는다")
    void mockFundTransfer_fail_transactionNotFound() {
        // given
        Long userId = 1L;
        Long transferId = 10L;

        when(remittanceTransactionRepository.findById(transferId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> remittanceTransactionService.mockFundTransfer(userId, transferId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(RemittanceTransactionErrorCode.REMITTANCE_TRANSACTION_NOT_FOUND);

        verifyNoInteractions(virtualAccountRepository);
        verifyNoInteractions(mockBankAccountService);
        verifyNoInteractions(companyPoolService);
    }

    @Test
    @DisplayName("실패: PENDING 상태가 아니면 Mock 입금 확인을 처리하지 않는다")
    void mockFundTransfer_fail_invalidTransferStatus() {
        // given
        Long userId = 1L;
        Long transferId = 10L;
        RemittanceTransaction remittanceTransaction = createPendingTransaction(userId, transferId);
        remittanceTransaction.fund(30L);

        when(remittanceTransactionRepository.findById(transferId))
                .thenReturn(Optional.of(remittanceTransaction));

        // when & then
        assertThatThrownBy(() -> remittanceTransactionService.mockFundTransfer(userId, transferId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(RemittanceTransactionErrorCode.INVALID_REMITTANCE_TRANSACTION_STATUS);

        verifyNoInteractions(virtualAccountRepository);
        verifyNoInteractions(mockBankAccountService);
        verifyNoInteractions(companyPoolService);
    }

    @Test
    @DisplayName("실패: 연결된 가상계좌가 없으면 Mock 입금 확인을 처리하지 않는다")
    void mockFundTransfer_fail_virtualAccountNotFound() {
        // given
        Long userId = 1L;
        Long transferId = 10L;
        RemittanceTransaction remittanceTransaction = createPendingTransaction(userId, transferId);

        when(remittanceTransactionRepository.findById(transferId))
                .thenReturn(Optional.of(remittanceTransaction));
        when(virtualAccountRepository.findByRemittanceTransactionId(transferId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> remittanceTransactionService.mockFundTransfer(userId, transferId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(RemittanceTransactionErrorCode.VIRTUAL_ACCOUNT_NOT_FOUND);

        verifyNoInteractions(mockBankAccountService);
        verifyNoInteractions(companyPoolService);
    }

    @Test
    @DisplayName("실패: 가상계좌가 ISSUED 상태가 아니면 Mock 입금 확인을 처리하지 않는다")
    void mockFundTransfer_fail_invalidVirtualAccountStatus() {
        // given
        Long userId = 1L;
        Long transferId = 10L;
        RemittanceTransaction remittanceTransaction = createPendingTransaction(userId, transferId);
        VirtualAccount virtualAccount = createVirtualAccount(userId, transferId, LocalDateTime.now().plusMinutes(30));
        virtualAccount.pay(LocalDateTime.now());

        when(remittanceTransactionRepository.findById(transferId))
                .thenReturn(Optional.of(remittanceTransaction));
        when(virtualAccountRepository.findByRemittanceTransactionId(transferId))
                .thenReturn(Optional.of(virtualAccount));

        // when & then
        assertThatThrownBy(() -> remittanceTransactionService.mockFundTransfer(userId, transferId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(RemittanceTransactionErrorCode.INVALID_VIRTUAL_ACCOUNT_STATUS);

        verifyNoInteractions(mockBankAccountService);
        verifyNoInteractions(companyPoolService);
    }

    @Test
    @DisplayName("실패: 가상계좌가 만료되면 Mock 입금 확인을 처리하지 않는다")
    void mockFundTransfer_fail_virtualAccountExpired() {
        // given
        Long userId = 1L;
        Long transferId = 10L;
        RemittanceTransaction remittanceTransaction = createPendingTransaction(userId, transferId);
        VirtualAccount virtualAccount = createVirtualAccount(userId, transferId, LocalDateTime.now().minusMinutes(1));

        when(remittanceTransactionRepository.findById(transferId))
                .thenReturn(Optional.of(remittanceTransaction));
        when(virtualAccountRepository.findByRemittanceTransactionId(transferId))
                .thenReturn(Optional.of(virtualAccount));

        // when & then
        assertThatThrownBy(() -> remittanceTransactionService.mockFundTransfer(userId, transferId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(RemittanceTransactionErrorCode.VIRTUAL_ACCOUNT_EXPIRED);

        verifyNoInteractions(mockBankAccountService);
        verifyNoInteractions(companyPoolService);
    }

    private RemittanceTransactionCreateRequest createRequest() {
        return new RemittanceTransactionCreateRequest(
                "TQUOTE-001",
                RemittanceReason.LIVING_EXPENSES,
                "생활비 송금"
        );
    }

    private RemittanceQuoteSnapshot createQuote() {
        return new RemittanceQuoteSnapshot(
                "TQUOTE-001",
                1L,
                "KRW",
                new BigDecimal("1000000.00"),
                "USD",
                new BigDecimal("736.52"),
                new BigDecimal("1351.00000000"),
                new BigDecimal("8000.00"),
                new BigDecimal("1000000.00"),
                new BigDecimal("736.52")
        );
    }

    private RemittanceTransaction createPendingTransaction(Long userId, Long transferId) {
        RemittanceTransaction remittanceTransaction = RemittanceTransaction.create(
                userId,
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

        return remittanceTransaction;
    }

    private VirtualAccount createVirtualAccount(Long userId, Long transferId, LocalDateTime expiredAt) {
        return VirtualAccount.create(
                userId,
                transferId,
                "하나은행",
                "123-456789-123456",
                new BigDecimal("1008000.00"),
                "REMITTANCE",
                String.valueOf(transferId),
                LocalDateTime.now().minusMinutes(10),
                expiredAt
        );
    }
}
