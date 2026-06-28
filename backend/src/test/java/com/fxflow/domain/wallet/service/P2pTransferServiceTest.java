package com.fxflow.domain.wallet.service;

import com.fxflow.domain.ledger.entity.LedgerEntry;
import com.fxflow.domain.ledger.enums.LedgerDirection;
import com.fxflow.domain.ledger.repository.LedgerEntryRepository;
import com.fxflow.domain.transactionlimit.errorcode.TransactionLimitErrorCode;
import com.fxflow.domain.transactionlimit.validator.TransactionLimitValidator;
import com.fxflow.domain.user.entity.User;
import com.fxflow.domain.user.service.UserService;
import com.fxflow.domain.wallet.dto.request.P2pTransferRequest;
import com.fxflow.domain.wallet.dto.response.P2pTransferResponse;
import com.fxflow.domain.wallet.entity.Wallet;
import com.fxflow.domain.wallet.errorcode.LotErrorCode;
import com.fxflow.domain.wallet.errorcode.P2pTransferErrorCode;
import com.fxflow.domain.wallet.errorcode.WalletErrorCode;
import com.fxflow.domain.wallet.repository.P2pTransferRepository;
import com.fxflow.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class P2pTransferServiceTest {

    @Mock private P2pTransferRepository p2pTransferRepository;
    @Mock private WalletService walletService;
    @Mock private LedgerEntryRepository ledgerEntryRepository;
    @Mock private UserService userService;
    @Mock private TransactionLimitValidator transactionLimitValidator;
    @Mock private CurrencyLotService currencyLotService;

    @InjectMocks private P2pTransferService p2pTransferService;

    private Wallet senderWallet;
    private Wallet recipientWallet;
    private P2pTransferRequest request;

    private void givenWalletsSetUp() {
        given(walletService.getWalletWithLock(1L, "USD")).willReturn(senderWallet);
        given(walletService.getWalletWithLock(2L, "USD")).willReturn(recipientWallet);
        given(recipientWallet.getBalance()).willReturn(new BigDecimal("500.00"));
    }

    @BeforeEach
    void setUp() {
        senderWallet = mock(Wallet.class);
        recipientWallet = mock(Wallet.class);
        request = new P2pTransferRequest("recipient@example.com", "USD", new BigDecimal("200.00"), "memo");

        // stubs every test needs
        User recipient = mock(User.class);
        given(recipient.getId()).willReturn(2L);
        given(userService.getUserByEmail("recipient@example.com")).willReturn(recipient);
    }

    @Test
    @DisplayName("정상 송금 시 response의 transactionId가 TXN- 로 시작한다")
    void transfer_success_returnsTransactionIdWithPrefix() {
        givenWalletsSetUp();
        given(senderWallet.getBalance()).willReturn(new BigDecimal("1000.00"));
        given(senderWallet.withdraw(any())).willReturn(new BigDecimal("800.00"));
        given(recipientWallet.deposit(any())).willReturn(new BigDecimal("700.00"));

        P2pTransferResponse response = p2pTransferService.transfer(1L, request);

        assertThat(response.transactionId()).startsWith("TXN-");
    }

    @Test
    @DisplayName("정상 송금 시 sender balance가 차감된 금액으로 response에 반환된다")
    void transfer_success_responseContainsUpdatedSenderBalance() {
        givenWalletsSetUp();
        given(senderWallet.getBalance()).willReturn(new BigDecimal("1000.00"), new BigDecimal("800.00"));
        given(senderWallet.withdraw(any())).willReturn(new BigDecimal("800.00"));
        given(recipientWallet.deposit(any())).willReturn(new BigDecimal("700.00"));

        P2pTransferResponse response = p2pTransferService.transfer(1L, request);

        assertThat(response.senderBalanceAfter()).isEqualByComparingTo("800.00");
    }

    @Test
    @DisplayName("정상 송금 시 debit/credit ledger entry 두 건이 저장된다")
    void transfer_success_savesTwoLedgerEntries() {
        givenWalletsSetUp();
        given(senderWallet.getBalance()).willReturn(new BigDecimal("1000.00"));
        given(senderWallet.withdraw(any())).willReturn(new BigDecimal("800.00"));
        given(recipientWallet.deposit(any())).willReturn(new BigDecimal("700.00"));

        p2pTransferService.transfer(1L, request);

        then(ledgerEntryRepository).should().saveAll(argThat(entries -> {
            List<LedgerEntry> list = new ArrayList<>();
            entries.forEach(list::add);
            return list.size() == 2
                    && list.stream().anyMatch(e -> e.getLedgerDirection() == LedgerDirection.DEBIT)
                    && list.stream().anyMatch(e -> e.getLedgerDirection() == LedgerDirection.CREDIT);
        }));
    }

    @Test
    @DisplayName("정상 송금 시 debit/credit이 동일한 journalId를 공유한다")
    void transfer_success_ledgerEntriesShareJournalId() {
        givenWalletsSetUp();
        given(senderWallet.getBalance()).willReturn(new BigDecimal("1000.00"));
        given(senderWallet.withdraw(any())).willReturn(new BigDecimal("800.00"));
        given(recipientWallet.deposit(any())).willReturn(new BigDecimal("700.00"));

        p2pTransferService.transfer(1L, request);

        then(ledgerEntryRepository).should().saveAll(argThat(entries -> {
            List<LedgerEntry> list = new ArrayList<>();
            entries.forEach(list::add);
            Set<String> journalIds = list.stream()
                    .map(LedgerEntry::getJournalId)
                    .collect(Collectors.toSet());
            return journalIds.size() == 1;
        }));
    }

    @Test
    @DisplayName("정상 송금 시 ledger entry의 refId가 transactionId와 동일하다")
    void transfer_success_ledgerRefIdMatchesTransactionId() {
        givenWalletsSetUp();
        given(senderWallet.getBalance()).willReturn(new BigDecimal("1000.00"));
        given(senderWallet.withdraw(any())).willReturn(new BigDecimal("800.00"));
        given(recipientWallet.deposit(any())).willReturn(new BigDecimal("700.00"));

        P2pTransferResponse response = p2pTransferService.transfer(1L, request);

        then(ledgerEntryRepository).should().saveAll(argThat(entries -> {
            List<LedgerEntry> list = new ArrayList<>();
            entries.forEach(list::add);
            return list.stream().allMatch(e -> e.getRefId().equals(response.transactionId()));
        }));
    }

    @Test
    @DisplayName("자기 자신에게 송금 시 SELF_TRANSFER_NOT_ALLOWED 예외가 발생한다")
    void transfer_selfTransfer_throwsException() {
        assertThatThrownBy(() -> p2pTransferService.transfer(2L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", P2pTransferErrorCode.SELF_TRANSFER_NOT_ALLOWED);
    }

    @Test
    @DisplayName("잔액 부족 시 INSUFFICIENT_BALANCE 예외가 발생한다")
    void transfer_insufficientBalance_throwsException() {
        given(walletService.getWalletWithLock(1L, "USD")).willReturn(senderWallet);
        given(senderWallet.getBalance()).willReturn(new BigDecimal("100.00"));

        assertThatThrownBy(() -> p2pTransferService.transfer(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", WalletErrorCode.INSUFFICIENT_BALANCE);
    }

    @Test
    @DisplayName("수신자 한도 초과 시 예외가 발생한다")
    void transfer_recipientLimitExceeded_throwsException() {
        givenWalletsSetUp();
        given(senderWallet.getBalance()).willReturn(new BigDecimal("1000.00"));
        willThrow(new BusinessException(TransactionLimitErrorCode.WALLET_HOLDING_LIMIT_EXCEEDED))
                .given(transactionLimitValidator).validateWalletHolding(any(), any());

        assertThatThrownBy(() -> p2pTransferService.transfer(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", TransactionLimitErrorCode.WALLET_HOLDING_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("정상 송금 시 lot이 정산된다")
    void transfer_success_settlesLots() {
        givenWalletsSetUp();
        given(senderWallet.getBalance()).willReturn(new BigDecimal("1000.00"));
        given(senderWallet.withdraw(any())).willReturn(new BigDecimal("800.00"));
        given(recipientWallet.deposit(any())).willReturn(new BigDecimal("700.00"));

        p2pTransferService.transfer(1L, request);

        then(currencyLotService).should().settleLots(
                eq(senderWallet),
                eq(recipientWallet),
                eq(new BigDecimal("200.00")),
                anyString()
        );
    }

    @Test
    @DisplayName("lot 잔액 부족 시 예외가 발생하고 ledger가 저장되지 않는다")
    void transfer_insufficientLots_throwsException() {
        givenWalletsSetUp();
        given(senderWallet.getBalance()).willReturn(new BigDecimal("1000.00"));
        given(senderWallet.withdraw(any())).willReturn(new BigDecimal("800.00"));
        given(recipientWallet.deposit(any())).willReturn(new BigDecimal("700.00"));
        willThrow(new BusinessException(LotErrorCode.INSUFFICIENT_LOT_BALANCE))
                .given(currencyLotService).settleLots(any(), any(), any(), anyString());

        assertThatThrownBy(() -> p2pTransferService.transfer(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", LotErrorCode.INSUFFICIENT_LOT_BALANCE);

        then(ledgerEntryRepository).should(never()).saveAll(any());
    }
}