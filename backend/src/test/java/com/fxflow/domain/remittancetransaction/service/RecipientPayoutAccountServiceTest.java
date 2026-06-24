package com.fxflow.domain.remittancetransaction.service;

import com.fxflow.domain.mockbankaccount.entity.MockBankAccount;
import com.fxflow.domain.mockbankaccount.repository.MockBankAccountRepository;
import com.fxflow.domain.remittancetransaction.entity.Recipient;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecipientPayoutAccountServiceTest {

    @Mock
    private MockBankAccountRepository mockBankAccountRepository;

    @InjectMocks
    private RecipientPayoutAccountService recipientPayoutAccountService;

    @Test
    @DisplayName("성공: 수취인 지급용 모의계좌가 이미 있으면 새로 생성하지 않는다")
    void ensurePayoutAccount_success_alreadyExists() {
        // given
        Recipient recipient = createRecipient();
        MockBankAccount account = createMockBankAccount();

        when(mockBankAccountRepository.findByAccountNumberAndCurrencyCodeAndDeletedAtIsNull(
                recipient.getAccountNumber(),
                recipient.getCurrencyCode()
        )).thenReturn(Optional.of(account));

        // when
        recipientPayoutAccountService.ensurePayoutAccount(recipient);

        // then
        verify(mockBankAccountRepository, never()).save(any(MockBankAccount.class));
    }

    @Test
    @DisplayName("성공: 수취인 지급용 모의계좌가 없으면 테스트용 USD 계좌를 생성한다")
    void ensurePayoutAccount_success_createAccount() {
        // given
        Recipient recipient = createRecipient();

        when(mockBankAccountRepository.findByAccountNumberAndCurrencyCodeAndDeletedAtIsNull(
                recipient.getAccountNumber(),
                recipient.getCurrencyCode()
        )).thenReturn(Optional.empty());

        // when
        recipientPayoutAccountService.ensurePayoutAccount(recipient);

        // then
        verify(mockBankAccountRepository).save(argThat(account ->
                account.getUser() == null
                        && account.getRecipient() == recipient
                        && account.getOwnerType().equals("RECIPIENT")
                        && account.getAccountNumber().equals(recipient.getAccountNumber())
        ));
    }

    private Recipient createRecipient() {
        return Recipient.create(
                1L,
                "John Doe",
                "US",
                "USD",
                "Chase Bank",
                "1234567890"
        );
    }

    private MockBankAccount createMockBankAccount() {
        return MockBankAccount.createRecipientAccount(
                createRecipient(),
                "USD",
                "Chase Bank",
                "1234567890",
                BigDecimal.ZERO
        );
    }
}
