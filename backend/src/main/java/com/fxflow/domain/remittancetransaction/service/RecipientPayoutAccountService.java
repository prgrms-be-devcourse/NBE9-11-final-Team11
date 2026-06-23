package com.fxflow.domain.remittancetransaction.service;

import com.fxflow.domain.mockbankaccount.entity.MockBankAccount;
import com.fxflow.domain.mockbankaccount.repository.MockBankAccountRepository;
import com.fxflow.domain.remittancetransaction.entity.Recipient;
import com.fxflow.domain.user.entity.User;
import com.fxflow.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

// 수취인 생성
@Service
@RequiredArgsConstructor
public class RecipientPayoutAccountService {

    private final MockBankAccountRepository mockBankAccountRepository;
    private final UserRepository userRepository;

    /**
     * MVP에서는 외부 은행망 대신 수취인 USD 모의계좌로 지급을 시뮬레이션한다.
     * 등록된 계좌번호의 모의계좌가 없으면 지급 실패를 막기 위해 테스트용 계좌를 만든다.
     */
    @Transactional
    public void ensurePayoutAccount(Recipient recipient) {
        boolean exists = mockBankAccountRepository
                .findByAccountNumberAndCurrencyCodeAndDeletedAtIsNull(
                        recipient.getAccountNumber(),
                        recipient.getCurrencyCode()
                )
                .isPresent();

        if (exists) {
            return;
        }

        String recipientEmail = createRecipientEmail(recipient);
        User recipientUser = userRepository
                .findByEmail(recipientEmail)
                .orElseGet(() -> userRepository.save(User.create(
                        recipientEmail,
                        "remittance-recipient",
                        recipient.getName()
                )));

        MockBankAccount account = MockBankAccount.createSeedAccount(
                recipientUser,
                recipient.getCurrencyCode(),
                recipient.getBankName(),
                recipient.getAccountNumber(),
                BigDecimal.ZERO
        );
        mockBankAccountRepository.save(account);
    }

    private String createRecipientEmail(Recipient recipient) {
        String sanitizedAccountNumber = recipient.getAccountNumber().replaceAll("[^a-zA-Z0-9]", "");
        String accountPart = sanitizedAccountNumber.isBlank() ? "unknown" : sanitizedAccountNumber;
        String uniquePart = recipient.getId() == null
                ? UUID.randomUUID().toString()
                : "r" + recipient.getId();

        return "remittance-recipient-" + accountPart + "-" + uniquePart + "@fxflow.local";
    }
}
