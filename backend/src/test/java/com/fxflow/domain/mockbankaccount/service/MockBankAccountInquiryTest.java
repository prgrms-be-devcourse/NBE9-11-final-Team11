package com.fxflow.domain.mockbankaccount.service;

import com.fxflow.domain.mockbankaccount.dto.request.UsdMockAccountInquiryRequest;
import com.fxflow.domain.mockbankaccount.dto.response.UsdMockAccountInquiryResponse;
import com.fxflow.domain.mockbankaccount.entity.MockBankAccount;
import com.fxflow.domain.mockbankaccount.errorcode.MockBankAccountErrorCode;
import com.fxflow.domain.mockbankaccount.repository.MockBankAccountRepository;
import com.fxflow.domain.remittancetransaction.entity.RemittanceTransaction;
import com.fxflow.domain.remittancetransaction.enums.RemittanceMethod;
import com.fxflow.domain.remittancetransaction.enums.RemittanceReason;
import com.fxflow.domain.remittancetransaction.enums.TransferStatus;
import com.fxflow.domain.remittancetransaction.repository.RemittanceTransactionRepository;
import com.fxflow.domain.user.entity.User;
import com.fxflow.domain.user.repository.UserRepository;
import com.fxflow.domain.wallet.repository.WalletRepository;
import com.fxflow.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("USD 모의계좌 조회 - inquireUsdMockAccount")
class MockBankAccountInquiryTest {

    @Mock
    private MockBankAccountRepository mockBankAccountRepository;
    @Mock
    private RemittanceTransactionRepository remittanceTransactionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private WalletRepository walletRepository;

    @InjectMocks
    private MockBankAccountService mockBankAccountService;

    private static final String BANK_NAME = "Chase Bank";
    private static final String ACCOUNT_NUMBER = "123456789012";
    private static final String NAME = "John Doe";
    private static final String USD = "USD";

    // ── 성공 케이스 ──────────────────────────────────────────────

    @Nested
    @DisplayName("성공")
    class Success {

        @Test
        @DisplayName("수취 내역이 있으면 잔액과 페이징된 수취 내역을 반환한다")
        void inquire_success_withReceipts() {
            // given
            UsdMockAccountInquiryRequest request = new UsdMockAccountInquiryRequest(
                    BANK_NAME, ACCOUNT_NUMBER, NAME
            );
            MockBankAccount account = createMockBankAccount(new BigDecimal("1500.00"));
            RemittanceTransaction tx1 = createCompletedTransaction(1L, "1000000.00", "740.00");
            RemittanceTransaction tx2 = createCompletedTransaction(2L, "500000.00", "370.00");

            Pageable pageable = PageRequest.of(0, 20);
            Page<RemittanceTransaction> txPage = new PageImpl<>(List.of(tx1, tx2), pageable, 2);

            User sender1 = User.create("sender1@fxflow.app", "encoded", "김철수");
            User sender2 = User.create("sender2@fxflow.app", "encoded", "이영희");
            ReflectionTestUtils.setField(sender1, "id", 1L);
            ReflectionTestUtils.setField(sender2, "id", 2L);

            given(mockBankAccountRepository.findByAccountNumberAndBankNameAndNameAndCurrencyCode(
                    ACCOUNT_NUMBER, BANK_NAME, NAME, USD
            )).willReturn(Optional.of(account));
            given(remittanceTransactionRepository.findByRecipientAccountNumberAndStatus(
                    ACCOUNT_NUMBER, TransferStatus.COMPLETED, pageable
            )).willReturn(txPage);
            // N+1 개선: findAllById로 한 번에 조회
            given(userRepository.findAllById(List.of(1L, 2L)))
                    .willReturn(List.of(sender1, sender2));

            // when
            UsdMockAccountInquiryResponse response =
                    mockBankAccountService.inquireUsdMockAccount(request, pageable);

            // then
            assertThat(response.balance()).isEqualByComparingTo(new BigDecimal("1500.00"));
            assertThat(response.currencyCode()).isEqualTo(USD);
            assertThat(response.remittanceReceipts().content()).hasSize(2);
            assertThat(response.remittanceReceipts().totalElements()).isEqualTo(2);
            assertThat(response.remittanceReceipts().currentPage()).isZero();

            // findById가 아닌 findAllById 한 번만 호출됐는지 검증
            verify(userRepository).findAllById(List.of(1L, 2L));
            verify(userRepository, never()).findById(1L);
            verify(userRepository, never()).findById(2L);
        }

        @Test
        @DisplayName("수취 내역이 없으면 빈 리스트와 잔액 0을 반환한다")
        void inquire_success_emptyReceipts() {
            // given
            UsdMockAccountInquiryRequest request = new UsdMockAccountInquiryRequest(
                    BANK_NAME, ACCOUNT_NUMBER, NAME
            );
            MockBankAccount account = createMockBankAccount(BigDecimal.ZERO);
            Pageable pageable = PageRequest.of(0, 20);
            Page<RemittanceTransaction> emptyPage = new PageImpl<>(List.of(), pageable, 0);

            given(mockBankAccountRepository.findByAccountNumberAndBankNameAndNameAndCurrencyCode(
                    ACCOUNT_NUMBER, BANK_NAME, NAME, USD
            )).willReturn(Optional.of(account));
            given(remittanceTransactionRepository.findByRecipientAccountNumberAndStatus(
                    ACCOUNT_NUMBER, TransferStatus.COMPLETED, pageable
            )).willReturn(emptyPage);

            // when
            UsdMockAccountInquiryResponse response =
                    mockBankAccountService.inquireUsdMockAccount(request, pageable);

            // then
            assertThat(response.balance()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(response.remittanceReceipts().content()).isEmpty();
            assertThat(response.remittanceReceipts().totalElements()).isZero();
        }

        @Test
        @DisplayName("페이지 2 요청 시 두 번째 페이지 데이터를 반환한다")
        void inquire_success_secondPage() {
            // given
            UsdMockAccountInquiryRequest request = new UsdMockAccountInquiryRequest(
                    BANK_NAME, ACCOUNT_NUMBER, NAME
            );
            MockBankAccount account = createMockBankAccount(new BigDecimal("2000.00"));
            RemittanceTransaction tx = createCompletedTransaction(3L, "700000.00", "518.00");

            Pageable pageable = PageRequest.of(1, 1);
            Page<RemittanceTransaction> txPage = new PageImpl<>(List.of(tx), pageable, 2);

            User sender = User.create("sender3@fxflow.app", "encoded", "박민준");
            ReflectionTestUtils.setField(sender, "id", 3L);

            given(mockBankAccountRepository.findByAccountNumberAndBankNameAndNameAndCurrencyCode(
                    ACCOUNT_NUMBER, BANK_NAME, NAME, USD
            )).willReturn(Optional.of(account));
            given(remittanceTransactionRepository.findByRecipientAccountNumberAndStatus(
                    ACCOUNT_NUMBER, TransferStatus.COMPLETED, pageable
            )).willReturn(txPage);
            given(userRepository.findAllById(List.of(3L)))
                    .willReturn(List.of(sender));

            // when
            UsdMockAccountInquiryResponse response =
                    mockBankAccountService.inquireUsdMockAccount(request, pageable);

            // then
            assertThat(response.remittanceReceipts().currentPage()).isEqualTo(1);
            assertThat(response.remittanceReceipts().totalPages()).isEqualTo(2);
            assertThat(response.remittanceReceipts().content()).hasSize(1);
            assertThat(response.remittanceReceipts().content().get(0).senderName()).isEqualTo("박민준");
        }

        @Test
        @DisplayName("같은 송금인이 여러 번 보낸 경우 유저 조회를 1번만 한다 (distinct 검증)")
        void inquire_success_distinctUserQuery() {
            // given
            UsdMockAccountInquiryRequest request = new UsdMockAccountInquiryRequest(
                    BANK_NAME, ACCOUNT_NUMBER, NAME
            );
            MockBankAccount account = createMockBankAccount(new BigDecimal("2000.00"));
            // 같은 userId(1L)로 3번 송금
            RemittanceTransaction tx1 = createCompletedTransaction(1L, "500000.00", "370.00");
            RemittanceTransaction tx2 = createCompletedTransaction(1L, "500000.00", "370.00");
            RemittanceTransaction tx3 = createCompletedTransaction(1L, "500000.00", "370.00");

            Pageable pageable = PageRequest.of(0, 20);
            Page<RemittanceTransaction> txPage = new PageImpl<>(List.of(tx1, tx2, tx3), pageable, 3);

            User sender = User.create("sender1@fxflow.app", "encoded", "김철수");
            ReflectionTestUtils.setField(sender, "id", 1L);

            given(mockBankAccountRepository.findByAccountNumberAndBankNameAndNameAndCurrencyCode(
                    ACCOUNT_NUMBER, BANK_NAME, NAME, USD
            )).willReturn(Optional.of(account));
            given(remittanceTransactionRepository.findByRecipientAccountNumberAndStatus(
                    ACCOUNT_NUMBER, TransferStatus.COMPLETED, pageable
            )).willReturn(txPage);
            // distinct로 중복 제거 → userId 1L 하나만 조회
            given(userRepository.findAllById(List.of(1L)))
                    .willReturn(List.of(sender));

            // when
            UsdMockAccountInquiryResponse response =
                    mockBankAccountService.inquireUsdMockAccount(request, pageable);

            // then
            assertThat(response.remittanceReceipts().content()).hasSize(3);
            assertThat(response.remittanceReceipts().content())
                    .allMatch(r -> r.senderName().equals("김철수"));
            // 3건이지만 유저 조회는 1번만 (distinct 효과)
            verify(userRepository).findAllById(List.of(1L));
        }

        @Test
        @DisplayName("송금인 정보가 없으면 송금인명을 '알 수 없는 송금인'으로 반환한다")
        void inquire_success_unknownSender() {
            // given
            UsdMockAccountInquiryRequest request = new UsdMockAccountInquiryRequest(
                    BANK_NAME, ACCOUNT_NUMBER, NAME
            );
            MockBankAccount account = createMockBankAccount(new BigDecimal("740.00"));
            RemittanceTransaction tx = createCompletedTransaction(99L, "1000000.00", "740.00");

            Pageable pageable = PageRequest.of(0, 20);
            Page<RemittanceTransaction> txPage = new PageImpl<>(List.of(tx), pageable, 1);

            given(mockBankAccountRepository.findByAccountNumberAndBankNameAndNameAndCurrencyCode(
                    ACCOUNT_NUMBER, BANK_NAME, NAME, USD
            )).willReturn(Optional.of(account));
            given(remittanceTransactionRepository.findByRecipientAccountNumberAndStatus(
                    ACCOUNT_NUMBER, TransferStatus.COMPLETED, pageable
            )).willReturn(txPage);
            // userId=99 유저가 없는 경우 → 빈 리스트 반환
            given(userRepository.findAllById(List.of(99L)))
                    .willReturn(List.of());

            // when
            UsdMockAccountInquiryResponse response =
                    mockBankAccountService.inquireUsdMockAccount(request, pageable);

            // then
            assertThat(response.remittanceReceipts().content().get(0).senderName())
                    .isEqualTo("알 수 없는 송금인");
        }

        @Test
        @DisplayName("수취액과 잔액이 소수점 2자리로 포매팅된다")
        void inquire_success_amountFormatting() {
            // given
            UsdMockAccountInquiryRequest request = new UsdMockAccountInquiryRequest(
                    BANK_NAME, ACCOUNT_NUMBER, NAME
            );
            MockBankAccount account = createMockBankAccount(new BigDecimal("1234.5678"));
            RemittanceTransaction tx = createCompletedTransaction(1L, "1000000.12345678", "740.12345678");

            Pageable pageable = PageRequest.of(0, 20);
            Page<RemittanceTransaction> txPage = new PageImpl<>(List.of(tx), pageable, 1);

            User sender = User.create("sender@fxflow.app", "encoded", "홍길동");
            ReflectionTestUtils.setField(sender, "id", 1L);

            given(mockBankAccountRepository.findByAccountNumberAndBankNameAndNameAndCurrencyCode(
                    ACCOUNT_NUMBER, BANK_NAME, NAME, USD
            )).willReturn(Optional.of(account));
            given(remittanceTransactionRepository.findByRecipientAccountNumberAndStatus(
                    ACCOUNT_NUMBER, TransferStatus.COMPLETED, pageable
            )).willReturn(txPage);
            given(userRepository.findAllById(List.of(1L)))
                    .willReturn(List.of(sender));

            // when
            UsdMockAccountInquiryResponse response =
                    mockBankAccountService.inquireUsdMockAccount(request, pageable);

            // then
            // CurrencyAmountFormatter: USD → scale 2, RoundingMode.DOWN
            assertThat(response.balance()).isEqualByComparingTo(new BigDecimal("1234.56"));
            assertThat(response.remittanceReceipts().content().get(0).receiveAmount())
                    .isEqualByComparingTo(new BigDecimal("740.12"));
        }
    }

    // ── 실패 케이스 ──────────────────────────────────────────────

    @Nested
    @DisplayName("실패")
    class Failure {

        @Test
        @DisplayName("이름·은행명·계좌번호가 일치하는 USD 계좌가 없으면 MOCK_ACCOUNT_NOT_FOUND 예외가 발생한다")
        void inquire_fail_accountNotFound() {
            // given
            UsdMockAccountInquiryRequest request = new UsdMockAccountInquiryRequest(
                    BANK_NAME, ACCOUNT_NUMBER, NAME
            );
            Pageable pageable = PageRequest.of(0, 20);

            given(mockBankAccountRepository.findByAccountNumberAndBankNameAndNameAndCurrencyCode(
                    ACCOUNT_NUMBER, BANK_NAME, NAME, USD
            )).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() ->
                    mockBankAccountService.inquireUsdMockAccount(request, pageable)
            )
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(MockBankAccountErrorCode.MOCK_ACCOUNT_NOT_FOUND);
        }

        @Test
        @DisplayName("계좌번호는 맞지만 이름이 다르면 MOCK_ACCOUNT_NOT_FOUND 예외가 발생한다")
        void inquire_fail_nameMismatch() {
            // given
            UsdMockAccountInquiryRequest request = new UsdMockAccountInquiryRequest(
                    BANK_NAME, ACCOUNT_NUMBER, "Wrong Name"
            );
            Pageable pageable = PageRequest.of(0, 20);

            given(mockBankAccountRepository.findByAccountNumberAndBankNameAndNameAndCurrencyCode(
                    ACCOUNT_NUMBER, BANK_NAME, "Wrong Name", USD
            )).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() ->
                    mockBankAccountService.inquireUsdMockAccount(request, pageable)
            )
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(MockBankAccountErrorCode.MOCK_ACCOUNT_NOT_FOUND);
        }

        @Test
        @DisplayName("계좌번호는 맞지만 은행명이 다르면 MOCK_ACCOUNT_NOT_FOUND 예외가 발생한다")
        void inquire_fail_bankNameMismatch() {
            // given
            UsdMockAccountInquiryRequest request = new UsdMockAccountInquiryRequest(
                    "Wrong Bank", ACCOUNT_NUMBER, NAME
            );
            Pageable pageable = PageRequest.of(0, 20);

            given(mockBankAccountRepository.findByAccountNumberAndBankNameAndNameAndCurrencyCode(
                    ACCOUNT_NUMBER, "Wrong Bank", NAME, USD
            )).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() ->
                    mockBankAccountService.inquireUsdMockAccount(request, pageable)
            )
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(MockBankAccountErrorCode.MOCK_ACCOUNT_NOT_FOUND);
        }
    }

    // ── 헬퍼 ────────────────────────────────────────────────────

    private MockBankAccount createMockBankAccount(BigDecimal balance) {
        User user = User.create("recipient@fxflow.app", "encoded", NAME);
        MockBankAccount account = MockBankAccount.createSeedAccount(
                user, USD, BANK_NAME, ACCOUNT_NUMBER, balance
        );
        return account;
    }

    private RemittanceTransaction createCompletedTransaction(
            Long userId, String sendAmount, String receiveAmount
    ) {
        RemittanceTransaction tx = RemittanceTransaction.create(
                userId,
                1L,
                null,
                RemittanceMethod.BANK_TRANSFER.name(),
                null,
                null,
                null,
                null,
                null,
                "KRW",
                new BigDecimal(sendAmount),
                USD,
                new BigDecimal(receiveAmount),
                new BigDecimal("1351.00000000"),
                new BigDecimal("5000.00"),
                new BigDecimal(sendAmount),
                new BigDecimal(receiveAmount),
                RemittanceReason.LIVING_EXPENSES.name(),
                null,
                "idempotency-key-" + userId + "-" + System.nanoTime()
        );
        ReflectionTestUtils.setField(tx, "id", userId);
        tx.fund(10L);
        tx.complete(20L);
        return tx;
    }
}