package com.fxflow.domain.wallet.service;


import com.fxflow.domain.companypool.entity.CompanyPool;
import com.fxflow.domain.companypool.service.CompanyPoolService;
import com.fxflow.domain.fxrate.service.FxRateService;
import com.fxflow.domain.ledger.entity.LedgerEntry;
import com.fxflow.domain.ledger.enums.LedgerDirection;
import com.fxflow.domain.ledger.enums.LedgerEntryType;
import com.fxflow.domain.ledger.repository.LedgerEntryRepository;
import com.fxflow.domain.mockbankaccount.entity.MockBankAccount;
import com.fxflow.domain.mockbankaccount.errorcode.MockBankAccountErrorCode;
import com.fxflow.domain.mockbankaccount.service.MockBankAccountService;
import com.fxflow.domain.transactionlimit.errorcode.TransactionLimitErrorCode;
import com.fxflow.domain.transactionlimit.validator.TransactionLimitValidator;
import com.fxflow.domain.user.entity.User;
import com.fxflow.domain.user.service.UserService;
import com.fxflow.domain.userlimitusage.service.UserDailyUsageService;
import com.fxflow.domain.wallet.dto.request.ChargeRequest;
import com.fxflow.domain.wallet.dto.request.WithdrawRequest;
import com.fxflow.domain.wallet.dto.response.TransactionHistoryResponse;
import com.fxflow.domain.wallet.dto.response.TransactionResponse;
import com.fxflow.domain.wallet.dto.response.WalletBalanceResponse;
import com.fxflow.domain.wallet.entity.Wallet;
import com.fxflow.domain.wallet.errorcode.WalletErrorCode;
import com.fxflow.domain.wallet.policy.WalletPolicy;
import com.fxflow.domain.wallet.repository.WalletRepository;
import com.fxflow.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    private WalletRepository walletRepository;
    @Mock
    private FxRateService fxRateService;
    @Mock
    private LedgerEntryRepository ledgerEntryRepository;
    @Mock
    private MockBankAccountService mockBankAccountService;
    @Mock
    private CompanyPoolService companyPoolService;
    @Mock
    private UserService userService;
    @Mock
    private UserDailyUsageService userDailyUsageService;
    @Mock
    private TransactionLimitValidator transactionLimitValidator;


    @InjectMocks
    private WalletService walletService;
    private Wallet usdWallet;
    private Wallet krwWallet;

    @BeforeEach
    void setUp() {
        usdWallet = Wallet.create(null, "USD", new BigDecimal("100"));
        krwWallet = Wallet.create(null, "KRW", new BigDecimal("50000"));
    }

    @Test
    void getWalletBalance_success() {
        // given
        Long userId = 1L;
        when(walletRepository.findByUserId(userId))
                .thenReturn(List.of(usdWallet, krwWallet));
        when(fxRateService.getRate("USD", "KRW"))
                .thenReturn(new BigDecimal("1300"));
        when(fxRateService.getRate("KRW", "KRW"))
                .thenReturn(BigDecimal.ONE);
        // when
        WalletBalanceResponse response =
                walletService.getWalletBalance(userId);
        // then
        // 100 USD * 1300 + 50000 KRW
        // = 180000 KRW

        assertThat(response.totalKrw()).isEqualTo(180000L);
        assertThat(response.walletResponseList()).hasSize(2);
    }

    @Test
    void getWalletBalance_emptyWallets() {

        when(walletRepository.findByUserId(1L))
                .thenReturn(List.of());

        WalletBalanceResponse response =
                walletService.getWalletBalance(1L);

        assertThat(response.totalKrw()).isEqualTo(0L);
        assertThat(response.walletResponseList()).isEmpty();
    }

    // -- Transaction History --
    @Test
    @DisplayName("currency 지정 시 해당 지갑의 거래내역을 조회한다")
    void getTransactionHistory_withCurrency_success() {
        // given
        Long userId = 1L;
        String currency = "KRW";
        Wallet wallet = Wallet.create(null, currency, BigDecimal.valueOf(1000000));
        ReflectionTestUtils.setField(wallet, "id", 10L);

        Pageable pageable = PageRequest.of(0, 20);
        Page<LedgerEntry> emptyPage = new PageImpl<>(List.of());

        given(walletRepository.findByUserIdAndCurrencyCode(userId, currency))
                .willReturn(Optional.of(wallet));
        given(ledgerEntryRepository.findByWalletIdInAndFilters(List.of(10L), currency, null, null, null, pageable))
                .willReturn(emptyPage);

        // when
        TransactionHistoryResponse response = walletService.getTransactionHistory(
                userId, currency, null, null, null, pageable);

        // then
        assertThat(response.totalCount()).isEqualTo(0);
        verify(walletRepository).findByUserIdAndCurrencyCode(userId, currency);
    }

    @Test
    @DisplayName("해당 통화의 지갑이 없으면 WALLET_NOT_FOUND 예외가 발생한다")
    void getTransactionHistory_walletNotFound() {
        // given
        Long userId = 1L;
        String currency = "USD";
        Pageable pageable = PageRequest.of(0, 20);

        given(walletRepository.findByUserIdAndCurrencyCode(userId, currency))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() ->
                walletService.getTransactionHistory(userId, currency, null, null, null, pageable)
        )
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(WalletErrorCode.WALLET_NOT_FOUND);
    }

    @Test
    @DisplayName("from, to 날짜가 있으면 시작일 00:00:00, 종료일 23:59:59로 변환되어 조회된다")
    void getTransactionHistory_withDateRange() {
        // given
        Long userId = 1L;
        String currency = "KRW";
        LocalDate from = LocalDate.of(2025, 1, 1);
        LocalDate to = LocalDate.of(2025, 6, 1);
        Wallet wallet = Wallet.create(null, currency, BigDecimal.ZERO);
        ReflectionTestUtils.setField(wallet, "id", 10L);
        Pageable pageable = PageRequest.of(0, 20);

        given(walletRepository.findByUserIdAndCurrencyCode(userId, currency))
                .willReturn(Optional.of(wallet));
        given(ledgerEntryRepository.findByWalletIdInAndFilters(
                eq(List.of(10L)),
                eq(currency),
                isNull(),
                eq(LocalDateTime.of(2025, 1, 1, 0, 0, 0)),
                eq(LocalDateTime.of(2025, 6, 1, 23, 59, 59)),
                eq(pageable)
        )).willReturn(new PageImpl<>(List.of()));

        // when
        walletService.getTransactionHistory(userId, currency, null, from, to, pageable);

        // then
        verify(ledgerEntryRepository).findByWalletIdInAndFilters(
                eq(List.of(10L)),
                eq(currency),
                isNull(),
                eq(LocalDateTime.of(2025, 1, 1, 0, 0, 0)),
                eq(LocalDateTime.of(2025, 6, 1, 23, 59, 59)),
                eq(pageable)
        );
    }

    @Test
    @DisplayName("from, to가 없으면 null로 조회된다")
    void getTransactionHistory_withoutDateRange() {
        // given
        Long userId = 1L;
        String currency = "KRW";
        Wallet wallet = Wallet.create(null, currency, BigDecimal.ZERO);
        ReflectionTestUtils.setField(wallet, "id", 10L);
        Pageable pageable = PageRequest.of(0, 20);

        given(walletRepository.findByUserIdAndCurrencyCode(userId, currency))
                .willReturn(Optional.of(wallet));
        given(ledgerEntryRepository.findByWalletIdInAndFilters(List.of(10L), currency, null, null, null, pageable))
                .willReturn(new PageImpl<>(List.of()));

        // when
        walletService.getTransactionHistory(userId, currency, null, null, null, pageable);

        // then
        verify(ledgerEntryRepository).findByWalletIdInAndFilters(List.of(10L), currency, null, null, null, pageable);
    }

    @Test
    @DisplayName("currency가 없으면 유저의 모든 지갑 거래내역을 조회한다")
    void getTransactionHistory_withoutCurrency() {
        // given
        Long userId = 1L;
        Wallet krwWallet = Wallet.create(null, "KRW", BigDecimal.ZERO);
        Wallet usdWallet = Wallet.create(null, "USD", BigDecimal.ZERO);
        ReflectionTestUtils.setField(krwWallet, "id", 10L);
        ReflectionTestUtils.setField(usdWallet, "id", 11L);

        Pageable pageable = PageRequest.of(0, 20);

        given(walletRepository.findByUserId(userId))
                .willReturn(List.of(krwWallet, usdWallet));
        given(ledgerEntryRepository.findByWalletIdInAndFilters(List.of(10L, 11L), null, null, null, null, pageable))
                .willReturn(new PageImpl<>(List.of()));

        // when
        walletService.getTransactionHistory(userId, null, null, null, null, pageable);

        // then
        verify(ledgerEntryRepository).findByWalletIdInAndFilters(List.of(10L, 11L), null, null, null, null, pageable);
    }

    // -- Charge --
    @Test
    @DisplayName("월렛 충전 - 모의계좌 -> 원화 월렛")
    void charge_success() {
        // given
        Long userId = 1L;
        ChargeRequest request = new ChargeRequest(new BigDecimal("5000"));
        when(walletRepository.findByUserIdAndCurrencyCode(userId, "KRW")).thenReturn(Optional.of(krwWallet));

        MockBankAccount mockAccount = mock(MockBankAccount.class);
        when(mockAccount.getId())
                .thenReturn(10L);
        when(mockBankAccountService.getMockAccount(userId, "KRW"))
                .thenReturn(mockAccount);

        when(companyPoolService.deposit(anyString(), eq("KRW"), any(BigDecimal.class))).thenReturn(mock(CompanyPool.class));
        User mockUser = mock(User.class);
        when(userService.getUser(userId)).thenReturn(mockUser);

        // when
        TransactionResponse response = walletService.charge(userId, request);

        // then
        assertThat(response).isNotNull();
        assertThat(krwWallet.getBalance()).isEqualByComparingTo("55000");

        verify(mockBankAccountService, times(1))
                .withdraw(
                        eq(userId),
                        anyString(),
                        eq(10L),
                        eq(new BigDecimal("5000")),
                        eq("KRW")
                );
        verify(walletRepository, times(1)).save(krwWallet);
        // only wallet ledger entry saved here; bank entry is inside mocked withdraw
        verify(companyPoolService, times(1)).deposit(anyString(), eq("KRW"), eq(new BigDecimal("5000")));

        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository, times(1)).save(captor.capture());
        LedgerEntry savedEntry = captor.getValue();
        assertAll(
                () -> assertThat(savedEntry.getEntryType()).isEqualTo(LedgerEntryType.CHARGE),
                () -> assertThat(savedEntry.getLedgerDirection()).isEqualTo(LedgerDirection.CREDIT),
                () -> assertThat(savedEntry.getCurrencyCode()).isEqualTo("KRW"),
                () -> assertThat(savedEntry.getAmount()).isEqualByComparingTo("5000"),
                () -> assertThat(savedEntry.getBalanceBefore()).isEqualByComparingTo("50000"),
                () -> assertThat(savedEntry.getBalanceAfter()).isEqualByComparingTo("55000"),
                () -> assertThat(savedEntry.getWalletId()).isEqualTo(krwWallet.getId())
        );
    }

    @Test
    @DisplayName("월렛 충전 - invalid amount (0원 이하)")
    void charge_fail_invalidAmount() {

        // given
        Long userId = 1L;

        ChargeRequest request = new ChargeRequest(

                BigDecimal.ZERO
        );

        // when & then
        assertThatThrownBy(() ->
                walletService.charge(userId, request)
        )
                .isInstanceOf(BusinessException.class)
                .hasMessage(WalletErrorCode.INVALID_AMOUNT.getMessage());

        verify(walletRepository, never()).save(any());
        verify(ledgerEntryRepository, never()).save(any());
    }

    @Test
    @DisplayName("월렛 충전 - 보유 한도 초과")
    void charge_fail_walletLimitExceeded() {

        // given
        Long userId = 1L;
        String currency = "KRW";

        BigDecimal initialBalance = new BigDecimal("1999999");
        BigDecimal chargeAmount = new BigDecimal("2");
        BigDecimal walletLimit = new BigDecimal("2000000");

        User user = User.create("email", "password", "name");
        ReflectionTestUtils.setField(user, "id", userId);
        ReflectionTestUtils.setField(user, "walletLimitKrw", walletLimit);

        Wallet wallet = Wallet.create(user, currency, initialBalance);

        when(userService.getUser(userId)).thenReturn(user);

        when(walletRepository.findByUserIdAndCurrencyCode(userId, currency))
                .thenReturn(Optional.of(wallet));

        ChargeRequest request = new ChargeRequest(chargeAmount);

        willThrow(new BusinessException(TransactionLimitErrorCode.WALLET_HOLDING_LIMIT_EXCEEDED))
                .given(transactionLimitValidator)
                .validateWalletHolding(any(User.class), any(BigDecimal.class));

        // when & then
        assertThatThrownBy(() ->
                walletService.charge(userId, request)
        )
                .isInstanceOf(BusinessException.class)
                .hasMessage(TransactionLimitErrorCode.WALLET_HOLDING_LIMIT_EXCEEDED.getMessage());

        verify(walletRepository, never()).save(any());
        verify(ledgerEntryRepository, never()).save(any());
    }

    @Test
    @DisplayName("월렛 충전 - 다른 유저의 모의계좌로 충전 시도")
    void charge_fail_bankAccountNotOwned() {
        // given
        Long userId = 1L;
        Long otherUsersBankAccountId = 99L;

        ChargeRequest request = new ChargeRequest(new BigDecimal("5000"));

        when(walletRepository.findByUserIdAndCurrencyCode(userId, "KRW"))
                .thenReturn(Optional.of(krwWallet));

        // mock bank account returned from service
        MockBankAccount mockAccount = mock(MockBankAccount.class);

        when(mockAccount.getId())
                .thenReturn(otherUsersBankAccountId);

        when(mockBankAccountService.getMockAccount(userId, "KRW"))
                .thenReturn(mockAccount);

        // when charge tries to withdraw -> throw because account doesn't belong to user
        doThrow(new BusinessException(MockBankAccountErrorCode.MOCK_ACCOUNT_NOT_FOUND))
                .when(mockBankAccountService)
                .withdraw(
                        eq(userId),
                        anyString(),
                        eq(otherUsersBankAccountId),
                        eq(new BigDecimal("5000")),
                        eq("KRW")
                );

        User mockUser = mock(User.class);
        when(userService.getUser(userId))
                .thenReturn(mockUser);

        // when & then
        assertThatThrownBy(() ->
                walletService.charge(userId, request)
        )
                .isInstanceOf(BusinessException.class)
                .hasMessage(MockBankAccountErrorCode.MOCK_ACCOUNT_NOT_FOUND.getMessage());

        verify(walletRepository, never()).save(any());
        verify(ledgerEntryRepository, never()).save(any());
        verify(companyPoolService, never())
                .deposit(anyString(), anyString(), any(BigDecimal.class));
    }

    // -- Withdraw --
    @Test
    @DisplayName("월렛 출금 - 원화 월렛 -> 모의계좌")
    void withdraw_success() {
        // given
        Long userId = 1L;
        WithdrawRequest request = new WithdrawRequest(10L, new BigDecimal("5000"));
        when(walletRepository.findByUserIdAndCurrencyCode(userId, "KRW")).thenReturn(Optional.of(krwWallet));

        MockBankAccount mockAccount = mock(MockBankAccount.class);
        when(mockAccount.getId())
                .thenReturn(10L);
        when(mockBankAccountService.getMockAccount(userId, "KRW"))
                .thenReturn(mockAccount);

        User mockUser = mock(User.class);
        when(userService.getUser(userId)).thenReturn(mockUser);

        // when
        TransactionResponse response = walletService.withdraw(userId, request);

        // then
        assertThat(response).isNotNull();
        assertThat(krwWallet.getBalance()).isEqualByComparingTo("45000");

        verify(mockBankAccountService, times(1))
                .deposit(
                        eq(userId),
                        anyString(),
                        eq(10L),
                        eq(new BigDecimal("5000")),
                        eq("KRW")
                );
        verify(walletRepository, times(1)).save(krwWallet);
        verify(companyPoolService, times(1)).withdraw(anyString(), eq("KRW"), eq(new BigDecimal("5000")));

        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository, times(1)).save(captor.capture());
        LedgerEntry savedEntry = captor.getValue();
        assertAll(
                () -> assertThat(savedEntry.getEntryType()).isEqualTo(LedgerEntryType.WITHDRAW),
                () -> assertThat(savedEntry.getLedgerDirection()).isEqualTo(LedgerDirection.DEBIT),
                () -> assertThat(savedEntry.getCurrencyCode()).isEqualTo("KRW"),
                () -> assertThat(savedEntry.getAmount()).isEqualByComparingTo("5000"),
                () -> assertThat(savedEntry.getBalanceBefore()).isEqualByComparingTo("50000"),
                () -> assertThat(savedEntry.getBalanceAfter()).isEqualByComparingTo("45000"),
                () -> assertThat(savedEntry.getWalletId()).isEqualTo(krwWallet.getId()),
                () -> assertThat(savedEntry.getMockBankAccountId()).isNull(),
                () -> assertThat(savedEntry.getCompanyPoolId()).isNull()
        );
    }

    @Test
    @DisplayName("월렛 출금 - invalid amount (0원 이하)")
    void withdraw_fail_invalidAmount() {
        // given
        Long userId = 1L;
        WithdrawRequest request = new WithdrawRequest(10L, BigDecimal.ZERO);

        // when & then
        assertThatThrownBy(() ->
                walletService.withdraw(userId, request)
        )
                .isInstanceOf(BusinessException.class)
                .hasMessage(WalletErrorCode.INVALID_AMOUNT.getMessage());

        verify(walletRepository, never()).save(any());
        verify(ledgerEntryRepository, never()).save(any());
    }

    @Test
    @DisplayName("월렛 출금 - 200만원 초과 금액 출금 시도")
    void withdraw_fail_amountExceedsLimit() {
        // given
        Long userId = 1L;
        WithdrawRequest request = new WithdrawRequest(10L, WalletPolicy.MAX_KRW_BALANCE.add(BigDecimal.ONE));

        // when & then
        assertThatThrownBy(() ->
                walletService.withdraw(userId, request)
        )
                .isInstanceOf(BusinessException.class)
                .hasMessage(WalletErrorCode.INVALID_AMOUNT.getMessage());

        verify(walletRepository, never()).save(any());
        verify(ledgerEntryRepository, never()).save(any());
    }

    @Test
    @DisplayName("월렛 출금 - 잔액 부족")
    void withdraw_fail_insufficientBalance() {
        // given
        Long userId = 1L;
        WithdrawRequest request = new WithdrawRequest(10L, new BigDecimal("60000")); // more than krwWallet's 50000

        when(walletRepository.findByUserIdAndCurrencyCode(userId, "KRW")).thenReturn(Optional.of(krwWallet));
        User mockUser = mock(User.class);
        when(userService.getUser(userId)).thenReturn(mockUser);

        // when & then
        assertThatThrownBy(() ->
                walletService.withdraw(userId, request)
        )
                .isInstanceOf(BusinessException.class)
                .hasMessage(WalletErrorCode.INSUFFICIENT_BALANCE.getMessage());

        verify(walletRepository, never()).save(any());
        verify(ledgerEntryRepository, never()).save(any());
        verify(mockBankAccountService, never()).deposit(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("월렛 출금 - 다른 유저의 모의계좌로 출금 시도")
    void withdraw_fail_bankAccountNotOwned() {
        // given
        Long userId = 1L;
        Long otherUsersBankAccountId = 99L;

        WithdrawRequest request =
                new WithdrawRequest(otherUsersBankAccountId, new BigDecimal("5000"));

        when(walletRepository.findByUserIdAndCurrencyCode(userId, "KRW"))
                .thenReturn(Optional.of(krwWallet));

        MockBankAccount mockAccount = mock(MockBankAccount.class);

        when(mockAccount.getId())
                .thenReturn(otherUsersBankAccountId);

        when(mockBankAccountService.getMockAccount(userId, "KRW"))
                .thenReturn(mockAccount);

        doThrow(new BusinessException(MockBankAccountErrorCode.MOCK_ACCOUNT_NOT_FOUND))
                .when(mockBankAccountService)
                .deposit(
                        eq(userId),
                        anyString(),
                        eq(otherUsersBankAccountId),
                        eq(new BigDecimal("5000")),
                        eq("KRW")
                );

        // when & then
        assertThatThrownBy(() ->
                walletService.withdraw(userId, request)
        )
                .isInstanceOf(BusinessException.class)
                .hasMessage(MockBankAccountErrorCode.MOCK_ACCOUNT_NOT_FOUND.getMessage());

        verify(walletRepository, never()).save(any());
        verify(ledgerEntryRepository, never()).save(any());
        verify(companyPoolService, never())
                .withdraw(anyString(), anyString(), any(BigDecimal.class));
    }
}