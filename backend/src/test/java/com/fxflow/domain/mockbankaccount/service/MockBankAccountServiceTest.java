package com.fxflow.domain.mockbankaccount.service;

import com.fxflow.domain.mockbankaccount.dto.response.MockBankAccountResponse;
import com.fxflow.domain.mockbankaccount.entity.MockBankAccount;
import com.fxflow.domain.mockbankaccount.errorcode.MockBankAccountErrorCode;
import com.fxflow.domain.mockbankaccount.repository.MockBankAccountRepository;
import com.fxflow.domain.ledger.repository.LedgerEntryRepository;
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

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class MockBankAccountServiceTest {

    @Mock
    private MockBankAccountRepository mockBankAccountRepository;
    @Mock
    private LedgerEntryRepository ledgerEntryRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private WalletRepository walletRepository;

    @InjectMocks
    private MockBankAccountService mockBankAccountService;

    private static final Long USER_ID = 1L;
    private static final String KRW = "KRW";

    @Nested
    @DisplayName("getMyAccount - 본인 모의계좌(KRW) 잔액 조회")
    class GetMyAccount {

        @Test
        @DisplayName("성공: 연결된 KRW 모의계좌가 있으면 잔액 정보를 반환한다")
        void success() throws Exception {
            // given
            MockBankAccount account = createAccount("신한은행", "110123456789", new BigDecimal("8500000"));
            given(mockBankAccountRepository.findFirstByUser_IdAndCurrencyCodeAndDeletedAtIsNull(USER_ID, KRW))
                    .willReturn(Optional.of(account));

            // when
            MockBankAccountResponse response = mockBankAccountService.getMyAccount(USER_ID);

            // then
            assertThat(response.bankName()).isEqualTo("신한은행");
            assertThat(response.accountNumber()).isEqualTo("110123456789");
            assertThat(response.currency()).isEqualTo(KRW);
            assertThat(response.balance()).isEqualByComparingTo(new BigDecimal("8500000"));
        }

        @Test
        @DisplayName("실패: 연결된 모의계좌가 없으면 MOCK_ACCOUNT_NOT_FOUND 예외가 발생한다")
        void notFound() {
            // given
            given(mockBankAccountRepository.findFirstByUser_IdAndCurrencyCodeAndDeletedAtIsNull(USER_ID, KRW))
                    .willReturn(Optional.empty());
            // when & then
            assertThatThrownBy(() -> mockBankAccountService.getMyAccount(USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(MockBankAccountErrorCode.MOCK_ACCOUNT_NOT_FOUND);
        }
    }

    // MockBankAccount.create()는 BigDecimal("10000000") 고정값이라
    // 테스트용 잔액을 자유롭게 세팅하기 위해 리플렉션으로 balance 필드를 직접 주입한다.
    private MockBankAccount createAccount(String bankName, String accountNumber, BigDecimal balance) throws Exception {
        User user = User.create("test@fxflow.app", "encoded", "홍길동");
        MockBankAccount account = MockBankAccount.create(user, bankName, accountNumber);

        Field balanceField = MockBankAccount.class.getDeclaredField("balance");
        balanceField.setAccessible(true);
        balanceField.set(account, balance);

        return account;
    }
}