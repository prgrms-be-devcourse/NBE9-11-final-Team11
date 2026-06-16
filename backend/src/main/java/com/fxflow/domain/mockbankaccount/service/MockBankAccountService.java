package com.fxflow.domain.mockbankaccount.service;


import com.fxflow.domain.mockbankaccount.dto.response.MockBankLinkResponse;
import com.fxflow.domain.mockbankaccount.entity.MockBankAccount;
import com.fxflow.domain.mockbankaccount.errorcode.MockBankAccountErrorCode;
import com.fxflow.domain.mockbankaccount.repository.MockBankAccountRepository;
import com.fxflow.domain.user.entity.User;
import com.fxflow.domain.user.errorcode.UserErrorCode;
import com.fxflow.domain.user.repository.UserRepository;
import com.fxflow.domain.wallet.entity.Wallet;
import com.fxflow.domain.wallet.repository.WalletRepository;
import com.fxflow.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class MockBankAccountService {

    private static final String KRW = "KRW";
    private static final String USD="USD";

    private final MockBankAccountRepository mockBankAccountRepository;
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;

    private static final Pattern ACCOUNT_NUMBER_PATTERN = Pattern.compile("^[0-9-]+$");
    private static final int ACCOUNT_NUMBER_DIGIT_LENGTH = 12;
    /**
     * KRW 모의계좌 연결
     * 유저당 1개만 허용, 계좌번호 중복 불가
     */
    @Transactional
    public MockBankLinkResponse linkAccount(Long userId, String bankName, String accountNumber) {
        log.info("[모의계좌 연결 시작] userId={}, bankName={}", userId, bankName);

        validateAccountNumberFormat(accountNumber);

        if (mockBankAccountRepository.existsByUserIdAndCurrencyCode(userId, KRW)) {
            log.warn("[모의계좌 연결 실패] 이미 연결됨 — userId={}", userId);
            throw new BusinessException(MockBankAccountErrorCode.MOCK_ACCOUNT_ALREADY_LINKED);
        }

        if (mockBankAccountRepository.existsByAccountNumber(accountNumber)) {
            log.warn("[모의계좌 연결 실패] 계좌번호 중복 — accountNumber={}", accountNumber);
            throw new BusinessException(MockBankAccountErrorCode.MOCK_ACCOUNT_NUMBER_DUPLICATED);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        MockBankAccount account = MockBankAccount.create(user, bankName, accountNumber);
        mockBankAccountRepository.save(account);

        List<Wallet> wallets = createWallets(user);

        log.info("[모의계좌 연결 완료] userId={}, accountNumber={}", userId, accountNumber);
        return MockBankLinkResponse.of(account, wallets);
    }

    /**
     * 계좌번호 형식 검증 (공통 규칙)
     * - 숫자와 하이픈만 허용
     * - 하이픈 제외 숫자 자릿수: 12자리 고정
     */
    private void validateAccountNumberFormat(String accountNumber) {
        if (accountNumber == null || accountNumber.isBlank()) {
            log.warn("[모의계좌 연결 실패] 계좌번호 누락");
            throw new BusinessException(MockBankAccountErrorCode.MOCK_ACCOUNT_INVALID_FORMAT);
        }

        if (!ACCOUNT_NUMBER_PATTERN.matcher(accountNumber).matches()) {
            log.warn("[모의계좌 연결 실패] 형식 오류(숫자/하이픈 외 문자) — accountNumber={}", accountNumber);
            throw new BusinessException(MockBankAccountErrorCode.MOCK_ACCOUNT_INVALID_FORMAT);
        }

        String digitsOnly = accountNumber.replace("-", "");
        if (digitsOnly.length() != ACCOUNT_NUMBER_DIGIT_LENGTH) {
            log.warn("[모의계좌 연결 실패] 자릿수 오류 — accountNumber={}", accountNumber);
            throw new BusinessException(MockBankAccountErrorCode.MOCK_ACCOUNT_INVALID_FORMAT);
        }
    }
    /**
     * KRW/USD Wallet 생성 (0원 시작)
     * 이미 존재하면 생성하지 않음 — 재연결/중복 호출 방어
     */
    private List<Wallet> createWallets(User user) {
        Wallet krwWallet = walletRepository.findByUserIdAndCurrencyCode(user.getId(), KRW)
                .orElseGet(() -> walletRepository.save(Wallet.create(user, KRW, BigDecimal.ZERO)));
        Wallet usdWallet = walletRepository.findByUserIdAndCurrencyCode(user.getId(), USD)
                .orElseGet(() -> walletRepository.save(Wallet.create(user, USD, BigDecimal.ZERO)));

        log.info("[Wallet 생성] userId={}, krwWalletId={}, usdWalletId={}", user.getId(), krwWallet.getId(), usdWallet.getId());

        return List.of(krwWallet, usdWallet);
    }
}
