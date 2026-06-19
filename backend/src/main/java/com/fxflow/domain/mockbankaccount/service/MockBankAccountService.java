package com.fxflow.domain.mockbankaccount.service;

import com.fxflow.domain.ledger.entity.LedgerEntry;
import com.fxflow.domain.ledger.enums.LedgerDirection;
import com.fxflow.domain.ledger.enums.LedgerEntryType;
import com.fxflow.domain.ledger.enums.LedgerRefType;
import com.fxflow.domain.ledger.repository.LedgerEntryRepository;
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
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class MockBankAccountService {
    private static final String KRW = "KRW";
    private static final String USD = "USD";

    private final MockBankAccountRepository mockBankAccountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private static final Pattern ACCOUNT_NUMBER_PATTERN = Pattern.compile("^[0-9]+$");
    private static final int ACCOUNT_NUMBER_DIGIT_LENGTH = 12;

    @Transactional
    public void withdraw(Long userId, String journalId, Long bankAccountId, BigDecimal amount, String currencyCode) {
        MockBankAccount bankAccount = mockBankAccountRepository.findByIdAndUserId(bankAccountId, userId)
                .orElseThrow(() -> new BusinessException(MockBankAccountErrorCode.MOCK_ACCOUNT_NOT_FOUND));

        BigDecimal balanceBefore = bankAccount.getBalance();
        BigDecimal balanceAfter = balanceBefore.subtract(amount);
        if (balanceAfter.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(MockBankAccountErrorCode.MOCK_ACCOUNT_INSUFFICIENT_BALANCE);
        }
        bankAccount.withdraw(amount);
        LedgerEntry entry = LedgerEntry.create(
                journalId,
                LedgerEntryType.CHARGE,
                LedgerDirection.DEBIT,
                null,
                bankAccountId,
                null,
                currencyCode,
                amount,
                balanceBefore,
                balanceAfter,
                null,
                null
        );
        ledgerEntryRepository.save(entry);
        mockBankAccountRepository.save(bankAccount);
    }

    @Transactional
    public void deposit(Long userId, String journalId, Long bankAccountId, BigDecimal amount, String currencyCode) {
        MockBankAccount bankAccount = mockBankAccountRepository.findByIdAndUserId(bankAccountId, userId)
                .orElseThrow(() -> new BusinessException(MockBankAccountErrorCode.MOCK_ACCOUNT_NOT_FOUND));

        BigDecimal balanceBefore = bankAccount.getBalance();
        BigDecimal balanceAfter = balanceBefore.add(amount);

        bankAccount.deposit(amount);

        LedgerEntry entry = LedgerEntry.create(
                journalId,
                LedgerEntryType.WITHDRAW,
                LedgerDirection.CREDIT,
                null,
                bankAccountId,
                null,
                currencyCode,
                amount,
                balanceBefore,
                balanceAfter,
                null,
                null
        );

        ledgerEntryRepository.save(entry);
        mockBankAccountRepository.save(bankAccount);
    }


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
     * 계좌번호 형식 검증
     * - 숫자만 허용 (하이픈 등 구분자 불가)
     * - 12자리 고정
     */
    private void validateAccountNumberFormat(String accountNumber) {
        if (accountNumber == null || accountNumber.isBlank()) {
            log.warn("[모의계좌 연결 실패] 계좌번호 누락");
            throw new BusinessException(MockBankAccountErrorCode.MOCK_ACCOUNT_INVALID_FORMAT);
        }

        if (!ACCOUNT_NUMBER_PATTERN.matcher(accountNumber).matches()) {
            log.warn("[모의계좌 연결 실패] 형식 오류(숫자 외 문자 포함) — accountNumber={}", accountNumber);
            throw new BusinessException(MockBankAccountErrorCode.MOCK_ACCOUNT_INVALID_FORMAT);
        }

        if (accountNumber.length() != ACCOUNT_NUMBER_DIGIT_LENGTH) {
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

    /**
     * Wallet 충전/출금이 아니므로 LedgerEntryType.TRANSFER로 기록한다.
     */
    @Transactional
    public Long withdrawForRemittance(
            Long userId,
            String journalId,
            BigDecimal amount,
            String currencyCode,
            String refId
    ) {
        MockBankAccount bankAccount = mockBankAccountRepository
                .findFirstByUser_IdAndCurrencyCodeAndDeletedAtIsNull(userId, currencyCode)
                .orElseThrow(() -> new BusinessException(MockBankAccountErrorCode.MOCK_ACCOUNT_NOT_FOUND));

        BigDecimal balanceBefore = bankAccount.getBalance();
        BigDecimal balanceAfter = balanceBefore.subtract(amount);

        if (balanceAfter.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(MockBankAccountErrorCode.MOCK_ACCOUNT_INSUFFICIENT_BALANCE);
        }

        bankAccount.withdraw(amount);

        LedgerEntry entry = LedgerEntry.create(
                journalId,
                LedgerEntryType.TRANSFER,
                LedgerDirection.DEBIT,
                null,
                bankAccount.getId(),
                null,
                currencyCode,
                amount,
                balanceBefore,
                balanceAfter,
                LedgerRefType.REMITTANCE.name(),
                refId
        );

        ledgerEntryRepository.save(entry);
        mockBankAccountRepository.save(bankAccount);

        return bankAccount.getId();
    }

    /**
     * TRF-08에서 송금자가 입력한 수취인 계좌번호로 외화를 입금한다.
     */
    @Transactional
    @Transactional
    public Long depositForRemittance(
            String journalId,
            String accountNumber,
            BigDecimal amount,
            String currencyCode,
            String refId
    ) {
        MockBankAccount bankAccount = mockBankAccountRepository
                .findByAccountNumberAndCurrencyCodeAndDeletedAtIsNullForUpdate(accountNumber, currencyCode)
                .orElseThrow(() -> new BusinessException(MockBankAccountErrorCode.MOCK_ACCOUNT_NOT_FOUND));

        return depositToMockAccount(journalId, bankAccount, amount, currencyCode, refId);
    }

    /**
     * TRF-08 지급 실패 시 TRF-07에서 차감했던 송금자 모의계좌로 원화를 환불한다.
     */
    @Transactional
    public Long refundForRemittance(
            String journalId,
            Long bankAccountId,
            BigDecimal amount,
            String currencyCode,
            String refId
    ) {
        MockBankAccount bankAccount = mockBankAccountRepository.findById(bankAccountId)
                .orElseThrow(() -> new BusinessException(MockBankAccountErrorCode.MOCK_ACCOUNT_NOT_FOUND));

        return depositToMockAccount(journalId, bankAccount, amount, currencyCode, refId);
    }

    /**
     * 해외송금 지급 및 환불에서 공통으로 사용하는 모의계좌 입금 처리 메서드다.
     * Wallet 입출금이 아니므로 LedgerEntryType.TRANSFER와 REMITTANCE 참조로 원장을 남긴다.
     */
    private Long depositToMockAccount(
            String journalId,
            MockBankAccount bankAccount,
            BigDecimal amount,
            String currencyCode,
            String refId
    ) {
        BigDecimal balanceBefore = bankAccount.getBalance();
        BigDecimal balanceAfter = balanceBefore.add(amount);

        bankAccount.deposit(amount);

        LedgerEntry entry = LedgerEntry.create(
                journalId,
                LedgerEntryType.TRANSFER,
                LedgerDirection.CREDIT,
                null,
                bankAccount.getId(),
                null,
                currencyCode,
                amount,
                balanceBefore,
                balanceAfter,
                LedgerRefType.REMITTANCE.name(),
                refId
        );

        ledgerEntryRepository.save(entry);
        mockBankAccountRepository.save(bankAccount);

        return bankAccount.getId();
    }
}
