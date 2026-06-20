package com.fxflow.domain.remittancetransaction.service;

import com.fxflow.domain.mockbankaccount.entity.MockBankAccount;
import com.fxflow.domain.mockbankaccount.repository.MockBankAccountRepository;
import com.fxflow.domain.remittancetransaction.entity.Recipient;
import com.fxflow.domain.user.entity.User;
import com.fxflow.domain.user.repository.UserRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecipientPayoutAccountServiceTest {

    @Mock
    private MockBankAccountRepository mockBankAccountRepository;

    @Mock
    private UserRepository userRepository;

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
        verify(userRepository, never()).save(any(User.class));
        verify(mockBankAccountRepository, never()).save(any(MockBankAccount.class));
    }

    @Test
    @DisplayName("성공: 수취인 지급용 모의계좌가 없으면 테스트용 USD 계좌를 생성한다")
    void ensurePayoutAccount_success_createAccount() {
        // given
        Recipient recipient = createRecipient();
        User recipientUser = User.create(
                "remittance-recipient-1234567890-test@fxflow.local",
                "remittance-recipient",
                recipient.getName()
        );

        when(mockBankAccountRepository.findByAccountNumberAndCurrencyCodeAndDeletedAtIsNull(
                recipient.getAccountNumber(),
                recipient.getCurrencyCode()
        )).thenReturn(Optional.empty());
        when(userRepository.findByEmail(anyString()))
                .thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(recipientUser);

        // when
        recipientPayoutAccountService.ensurePayoutAccount(recipient);

        // then
        verify(userRepository).findByEmail(argThat(email ->
                email.startsWith("remittance-recipient-1234567890-")
                        && email.endsWith("@fxflow.local")
        ));
        verify(userRepository).save(any(User.class));
        verify(mockBankAccountRepository).save(any(MockBankAccount.class));
    }

    private Recipient createRecipient() {
        return Recipient.create(
                1L,
                "John Doe",
                "US",
                "USD",
                "Chase Bank",
                "123-456 7890"
        );
    }

    private MockBankAccount createMockBankAccount() {
        return MockBankAccount.createSeedAccount(
                User.create("recipient@example.com", "password", "John Doe"),
                "USD",
                "Chase Bank",
                "123-456 7890",
                BigDecimal.ZERO
        );
    }
}
