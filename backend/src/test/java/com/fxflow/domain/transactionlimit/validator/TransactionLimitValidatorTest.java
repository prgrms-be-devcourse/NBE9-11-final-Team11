package com.fxflow.domain.transactionlimit.validator;

import com.fxflow.domain.transactionlimit.entity.TransactionLimit;
import com.fxflow.domain.transactionlimit.enums.LimitTier;
import com.fxflow.domain.transactionlimit.enums.LimitType;
import com.fxflow.domain.transactionlimit.errorcode.TransactionLimitErrorCode;
import com.fxflow.domain.transactionlimit.repository.TransactionLimitRepository;
import com.fxflow.domain.user.entity.User;
import com.fxflow.domain.userlimitusage.entity.UserAnnualUsage;
import com.fxflow.domain.userlimitusage.entity.UserDailyUsage;
import com.fxflow.domain.userlimitusage.entity.UserExchangeAnnualUsage;
import com.fxflow.domain.userlimitusage.entity.UserExchangeDailyUsage;
import com.fxflow.domain.userlimitusage.repository.UserAnnualUsageRepository;
import com.fxflow.domain.userlimitusage.repository.UserDailyUsageRepository;
import com.fxflow.domain.userlimitusage.repository.UserExchangeAnnualUsageRepository;
import com.fxflow.domain.userlimitusage.repository.UserExchangeDailyUsageRepository;
import com.fxflow.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionLimitValidatorTest {

    @Mock
    private TransactionLimitRepository transactionLimitRepository;

    @Mock
    private UserAnnualUsageRepository userAnnualUsageRepository;

    @Mock
    private UserDailyUsageRepository userDailyUsageRepository;

    @Mock
    private UserExchangeDailyUsageRepository userExchangeDailyUsageRepository;

    @Mock
    private UserExchangeAnnualUsageRepository userExchangeAnnualUsageRepository;

    @InjectMocks
    private TransactionLimitValidator validator;

    @Mock
    private User user;

    // ── 공통 Mock 데이터 ────────────────────────────────────────────────────

    private TransactionLimit mockLimit(LimitType limitType, LimitTier tier, String currencyCode, BigDecimal limitAmount) {
        return TransactionLimit.create(limitType, tier, currencyCode, limitAmount);
    }

    // ── 1. 건당 송금 한도 검증 ─────────────────────────────────────────────
    @Nested
    @DisplayName("건당 송금 한도 검증")
    class ValidatePerRemittance {

        @BeforeEach
        void setUp() {
            when(user.getId()).thenReturn(1L);
        }

        @Test
        @DisplayName("성공: 요청액이 한도 미만")
        void success() {
            when(transactionLimitRepository
                    .findByLimitTypeAndTierAndCurrencyCodeAndIsActiveTrue(
                            LimitType.PER_REMITTANCE, LimitTier.STANDARD, "USD"))
                    .thenReturn(Optional.of(
                            mockLimit(LimitType.PER_REMITTANCE, LimitTier.STANDARD, "USD", new BigDecimal("5000"))
                    ));

            assertThatCode(() -> validator.validatePerRemittance(user, new BigDecimal("3000")))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("실패: 요청액이 한도 초과")
        void fail_exceeded() {
            when(transactionLimitRepository
                    .findByLimitTypeAndTierAndCurrencyCodeAndIsActiveTrue(
                            LimitType.PER_REMITTANCE, LimitTier.STANDARD, "USD"))
                    .thenReturn(Optional.of(
                            mockLimit(LimitType.PER_REMITTANCE, LimitTier.STANDARD, "USD", new BigDecimal("5000"))
                    ));

            assertThatThrownBy(() -> validator.validatePerRemittance(user, new BigDecimal("6000")))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(TransactionLimitErrorCode.PER_REMITTANCE_LIMIT_EXCEEDED);
        }

        @Test
        @DisplayName("실패: 한도 정책 없음")
        void fail_policyNotFound() {
            when(transactionLimitRepository
                    .findByLimitTypeAndTierAndCurrencyCodeAndIsActiveTrue(
                            LimitType.PER_REMITTANCE, LimitTier.STANDARD, "USD"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> validator.validatePerRemittance(user, new BigDecimal("3000")))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(TransactionLimitErrorCode.LIMIT_POLICY_NOT_FOUND);
        }
    }

    // ── 2. 연간 송금 한도 검증 ─────────────────────────────────────────────
    @Nested
    @DisplayName("연간 송금 한도 검증")
    class ValidateAnnualRemittance {

        @BeforeEach
        void setUp() {
            when(user.getId()).thenReturn(1L);
            when(transactionLimitRepository
                    .findByLimitTypeAndTierAndCurrencyCodeAndIsActiveTrue(
                            LimitType.ANNUAL_REMITTANCE, LimitTier.STANDARD, "USD"))
                    .thenReturn(Optional.of(
                            mockLimit(LimitType.ANNUAL_REMITTANCE, LimitTier.STANDARD, "USD", new BigDecimal("100000"))
                    ));
        }

        @Test
        @DisplayName("성공: 누적액 + 요청액이 한도 미만")
        void success() {
            UserAnnualUsage usage = UserAnnualUsage.create(user, 2025);
            usage.addUsage(new BigDecimal("50000"));

            when(userAnnualUsageRepository.findByUserIdAndYear(1L, LocalDate.now().getYear()))
                    .thenReturn(Optional.of(usage));

            assertThatCode(() -> validator.validateAnnualRemittance(user, new BigDecimal("3000")))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("실패: 누적액 + 요청액이 한도 초과")
        void fail_exceeded() {
            UserAnnualUsage usage = UserAnnualUsage.create(user, 2025);
            usage.addUsage(new BigDecimal("98000"));

            when(userAnnualUsageRepository.findByUserIdAndYear(1L, LocalDate.now().getYear()))
                    .thenReturn(Optional.of(usage));

            assertThatThrownBy(() -> validator.validateAnnualRemittance(user, new BigDecimal("5000")))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(TransactionLimitErrorCode.ANNUAL_REMITTANCE_LIMIT_EXCEEDED);
        }

        @Test
        @DisplayName("성공: 첫 송금 (UserAnnualUsage 없는 경우)")
        void success_firstRemittance() {
            when(userAnnualUsageRepository.findByUserIdAndYear(1L, LocalDate.now().getYear()))
                    .thenReturn(Optional.empty());

            assertThatCode(() -> validator.validateAnnualRemittance(user, new BigDecimal("3000")))
                    .doesNotThrowAnyException();
        }
    }

    // ── 3. 월렛 보유 한도 검증 ─────────────────────────────────────────────
    @Nested
    @DisplayName("월렛 보유 한도 검증")
    class ValidateWalletHolding {

        @BeforeEach
        void setUp() {
            when(user.getId()).thenReturn(1L);
            when(user.getWalletLimitKrw()).thenReturn(new BigDecimal("2000000"));
        }

        @Test
        @DisplayName("성공: 변경후잔액이 한도 미만")
        void success() {
            assertThatCode(() -> validator.validateWalletHolding(user, new BigDecimal("1500000")))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("실패: 변경후잔액이 한도 초과")
        void fail_exceeded() {
            assertThatThrownBy(() -> validator.validateWalletHolding(user, new BigDecimal("2500000")))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(TransactionLimitErrorCode.WALLET_HOLDING_LIMIT_EXCEEDED);
        }
    }

    // ── 4. 일일 입금 한도 검증 (200만 가정)─────────────────────────────────────────────
    @Nested
    @DisplayName("일일 입금 한도 검증")
    class ValidateDailyDeposit {

        @BeforeEach
        void setUp() {
            when(user.getId()).thenReturn(1L);
            when(user.getLimitTier()).thenReturn(LimitTier.STANDARD);
            when(transactionLimitRepository
                    .findByLimitTypeAndTierAndCurrencyCodeAndIsActiveTrue(
                            LimitType.DAILY_DEPOSIT, LimitTier.STANDARD, "KRW"))
                    .thenReturn(Optional.of(
                            mockLimit(LimitType.DAILY_DEPOSIT, LimitTier.STANDARD, "KRW", new BigDecimal("2000000"))
                    ));
        }

        @Test
        @DisplayName("성공: 누적액 + 요청액이 한도 미만")
        void success() {
            UserDailyUsage usage = UserDailyUsage.create(user, LocalDate.now());
            usage.addDeposit(new BigDecimal("500000"));

            when(userDailyUsageRepository.findByUserIdAndUsageDate(1L, LocalDate.now()))
                    .thenReturn(Optional.of(usage));

            assertThatCode(() -> validator.validateDailyDeposit(user, new BigDecimal("1000000")))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("실패: 누적액 + 요청액이 한도 초과")
        void fail_exceeded() {
            UserDailyUsage usage = UserDailyUsage.create(user, LocalDate.now());
            usage.addDeposit(new BigDecimal("1500000"));

            when(userDailyUsageRepository.findByUserIdAndUsageDate(1L, LocalDate.now()))
                    .thenReturn(Optional.of(usage));

            assertThatThrownBy(() -> validator.validateDailyDeposit(user, new BigDecimal("1000000")))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(TransactionLimitErrorCode.DAILY_DEPOSIT_LIMIT_EXCEEDED);
        }

        @Test
        @DisplayName("성공: 첫 입금 (UserDailyUsage 없는 경우)")
        void success_firstDeposit() {
            when(userDailyUsageRepository.findByUserIdAndUsageDate(1L, LocalDate.now()))
                    .thenReturn(Optional.empty());

            assertThatCode(() -> validator.validateDailyDeposit(user, new BigDecimal("1000000")))
                    .doesNotThrowAnyException();
        }
    }

    // ── 5. 일일 출금 한도 검증 (200만 가정)─────────────────────────────────────────────
    @Nested
    @DisplayName("일일 출금 한도 검증")
    class ValidateDailyWithdrawal {

        @BeforeEach
        void setUp() {
            when(user.getId()).thenReturn(1L);
            when(user.getLimitTier()).thenReturn(LimitTier.STANDARD);
            when(transactionLimitRepository
                    .findByLimitTypeAndTierAndCurrencyCodeAndIsActiveTrue(
                            LimitType.DAILY_WITHDRAWAL, LimitTier.STANDARD, "KRW"))
                    .thenReturn(Optional.of(
                            mockLimit(LimitType.DAILY_WITHDRAWAL, LimitTier.STANDARD, "KRW", new BigDecimal("2000000"))
                    ));
        }

        @Test
        @DisplayName("성공: 누적액 + 요청액이 한도 미만")
        void success() {
            UserDailyUsage usage = UserDailyUsage.create(user, LocalDate.now());
            usage.addWithdrawal(new BigDecimal("500000"));

            when(userDailyUsageRepository.findByUserIdAndUsageDate(1L, LocalDate.now()))
                    .thenReturn(Optional.of(usage));

            assertThatCode(() -> validator.validateDailyWithdrawal(user, new BigDecimal("1000000")))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("실패: 누적액 + 요청액이 한도 초과")
        void fail_exceeded() {
            UserDailyUsage usage = UserDailyUsage.create(user, LocalDate.now());
            usage.addWithdrawal(new BigDecimal("1500000"));

            when(userDailyUsageRepository.findByUserIdAndUsageDate(1L, LocalDate.now()))
                    .thenReturn(Optional.of(usage));

            assertThatThrownBy(() -> validator.validateDailyWithdrawal(user, new BigDecimal("1000000")))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(TransactionLimitErrorCode.DAILY_WITHDRAWAL_LIMIT_EXCEEDED);
        }

        @Test
        @DisplayName("성공: 첫 출금 (UserDailyUsage 없는 경우)")
        void success_firstWithdrawal() {
            when(userDailyUsageRepository.findByUserIdAndUsageDate(1L, LocalDate.now()))
                    .thenReturn(Optional.empty());

            assertThatCode(() -> validator.validateDailyWithdrawal(user, new BigDecimal("1000000")))
                    .doesNotThrowAnyException();
        }
    }

    // ── 6. 1회 입금 한도 검증 (200만가정)─────────────────────────────────────────────
    @Nested
    @DisplayName("1회 입금 한도 검증")
    class ValidatePerDeposit {

        @BeforeEach
        void setUp() {
            when(user.getId()).thenReturn(1L);
            when(user.getLimitTier()).thenReturn(LimitTier.STANDARD);
            when(transactionLimitRepository
                    .findByLimitTypeAndTierAndCurrencyCodeAndIsActiveTrue(
                            LimitType.PER_DEPOSIT, LimitTier.STANDARD, "KRW"))
                    .thenReturn(Optional.of(
                            mockLimit(LimitType.PER_DEPOSIT, LimitTier.STANDARD, "KRW", new BigDecimal("2000000"))
                    ));
        }

        @Test
        @DisplayName("성공: 요청액이 한도 미만")
        void success() {
            assertThatCode(() -> validator.validatePerDeposit(user, new BigDecimal("1500000")))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("성공: 요청액이 한도와 동일")
        void success_equalToLimit() {
            assertThatCode(() -> validator.validatePerDeposit(user, new BigDecimal("2000000")))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("실패: 요청액이 한도 초과")
        void fail_exceeded() {
            assertThatThrownBy(() -> validator.validatePerDeposit(user, new BigDecimal("2500000")))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(TransactionLimitErrorCode.PER_DEPOSIT_LIMIT_EXCEEDED);
        }
    }

    // ── 7. 1회 출금 한도 검증 (200만 가정)─────────────────────────────────────────────
    @Nested
    @DisplayName("1회 출금 한도 검증")
    class ValidatePerWithdrawal {

        @BeforeEach
        void setUp() {
            when(user.getId()).thenReturn(1L);
            when(user.getLimitTier()).thenReturn(LimitTier.STANDARD);
            when(transactionLimitRepository
                    .findByLimitTypeAndTierAndCurrencyCodeAndIsActiveTrue(
                            LimitType.PER_WITHDRAWAL, LimitTier.STANDARD, "KRW"))
                    .thenReturn(Optional.of(
                            mockLimit(LimitType.PER_WITHDRAWAL, LimitTier.STANDARD, "KRW", new BigDecimal("2000000"))
                    ));
        }

        @Test
        @DisplayName("성공: 요청액이 한도 미만")
        void success() {
            assertThatCode(() -> validator.validatePerWithdrawal(user, new BigDecimal("1500000")))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("성공: 요청액이 한도와 동일")
        void success_equalToLimit() {
            assertThatCode(() -> validator.validatePerWithdrawal(user, new BigDecimal("2000000")))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("실패: 요청액이 한도 초과")
        void fail_exceeded() {
            assertThatThrownBy(() -> validator.validatePerWithdrawal(user, new BigDecimal("2500000")))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(TransactionLimitErrorCode.PER_WITHDRAWAL_LIMIT_EXCEEDED);
        }
    }

    // ── 8. 건당 환전 한도 검증 ─────────────────────────────────────────────
    @Nested
    @DisplayName("건당 환전 한도 검증")
    class ValidatePerExchange {

        @BeforeEach
        void setUp() {
            when(user.getId()).thenReturn(1L);
            when(user.getLimitTier()).thenReturn(LimitTier.STANDARD);
            when(transactionLimitRepository
                    .findByLimitTypeAndTierAndCurrencyCodeAndIsActiveTrue(
                            LimitType.PER_EXCHANGE, LimitTier.STANDARD, "KRW"))
                    .thenReturn(Optional.of(
                            mockLimit(LimitType.PER_EXCHANGE, LimitTier.STANDARD, "KRW", new BigDecimal("2000000"))
                    ));
        }

        @Test
        @DisplayName("성공: 요청액이 한도 미만")
        void success() {
            assertThatCode(() -> validator.validatePerExchange(user, new BigDecimal("1500000")))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("성공: 요청액이 한도와 동일")
        void success_equalToLimit() {
            assertThatCode(() -> validator.validatePerExchange(user, new BigDecimal("2000000")))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("실패: 요청액이 한도 초과")
        void fail_exceeded() {
            assertThatThrownBy(() -> validator.validatePerExchange(user, new BigDecimal("2500000")))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(TransactionLimitErrorCode.PER_EXCHANGE_LIMIT_EXCEEDED);
        }

        @Test
        @DisplayName("실패: 한도 정책 없음")
        void fail_policyNotFound() {
            when(transactionLimitRepository
                    .findByLimitTypeAndTierAndCurrencyCodeAndIsActiveTrue(
                            LimitType.PER_EXCHANGE, LimitTier.STANDARD, "KRW"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> validator.validatePerExchange(user, new BigDecimal("1000000")))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(TransactionLimitErrorCode.LIMIT_POLICY_NOT_FOUND);
        }
    }

    // ── 9. 일일 환전 한도 검증 ─────────────────────────────────────────────
    @Nested
    @DisplayName("일일 환전 한도 검증")
    class ValidateDailyExchange {

        @BeforeEach
        void setUp() {
            when(user.getId()).thenReturn(1L);
            when(user.getLimitTier()).thenReturn(LimitTier.STANDARD);
            when(transactionLimitRepository
                    .findByLimitTypeAndTierAndCurrencyCodeAndIsActiveTrue(
                            LimitType.DAILY_EXCHANGE, LimitTier.STANDARD, "KRW"))
                    .thenReturn(Optional.of(
                            mockLimit(LimitType.DAILY_EXCHANGE, LimitTier.STANDARD, "KRW", new BigDecimal("2000000"))
                    ));
        }

        @Test
        @DisplayName("성공: 누적액 + 요청액이 한도 미만")
        void success() {
            UserExchangeDailyUsage usage = UserExchangeDailyUsage.create(user, LocalDate.now());
            usage.addExchange(new BigDecimal("500000"));

            when(userExchangeDailyUsageRepository.findByUserIdAndUsageDate(1L, LocalDate.now()))
                    .thenReturn(Optional.of(usage));

            assertThatCode(() -> validator.validateDailyExchange(user, new BigDecimal("1000000")))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("실패: 누적액 + 요청액이 한도 초과")
        void fail_exceeded() {
            UserExchangeDailyUsage usage = UserExchangeDailyUsage.create(user, LocalDate.now());
            usage.addExchange(new BigDecimal("1500000"));

            when(userExchangeDailyUsageRepository.findByUserIdAndUsageDate(1L, LocalDate.now()))
                    .thenReturn(Optional.of(usage));

            assertThatThrownBy(() -> validator.validateDailyExchange(user, new BigDecimal("1000000")))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(TransactionLimitErrorCode.DAILY_EXCHANGE_LIMIT_EXCEEDED);
        }

        @Test
        @DisplayName("성공: 첫 환전 (UserExchangeDailyUsage 없는 경우)")
        void success_firstExchange() {
            when(userExchangeDailyUsageRepository.findByUserIdAndUsageDate(1L, LocalDate.now()))
                    .thenReturn(Optional.empty());

            assertThatCode(() -> validator.validateDailyExchange(user, new BigDecimal("1000000")))
                    .doesNotThrowAnyException();
        }
    }

    // ── 10. 연간 환전 한도 검증 ────────────────────────────────────────────
    @Nested
    @DisplayName("연간 환전 한도 검증")
    class ValidateAnnualExchange {

        @BeforeEach
        void setUp() {
            when(user.getId()).thenReturn(1L);
            when(transactionLimitRepository
                    .findByLimitTypeAndTierAndCurrencyCodeAndIsActiveTrue(
                            LimitType.ANNUAL_EXCHANGE, LimitTier.STANDARD, "USD"))
                    .thenReturn(Optional.of(
                            mockLimit(LimitType.ANNUAL_EXCHANGE, LimitTier.STANDARD, "USD", new BigDecimal("100000"))
                    ));
        }

        @Test
        @DisplayName("성공: 누적액 + 요청액이 한도 미만")
        void success() {
            UserExchangeAnnualUsage usage = UserExchangeAnnualUsage.create(user, 2025);
            usage.addExchange(new BigDecimal("50000"));

            when(userExchangeAnnualUsageRepository.findByUserIdAndYear(1L, LocalDate.now().getYear()))
                    .thenReturn(Optional.of(usage));

            assertThatCode(() -> validator.validateAnnualExchange(user, new BigDecimal("3000")))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("실패: 누적액 + 요청액이 한도 초과")
        void fail_exceeded() {
            UserExchangeAnnualUsage usage = UserExchangeAnnualUsage.create(user, 2025);
            usage.addExchange(new BigDecimal("98000"));

            when(userExchangeAnnualUsageRepository.findByUserIdAndYear(1L, LocalDate.now().getYear()))
                    .thenReturn(Optional.of(usage));

            assertThatThrownBy(() -> validator.validateAnnualExchange(user, new BigDecimal("5000")))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(TransactionLimitErrorCode.ANNUAL_EXCHANGE_LIMIT_EXCEEDED);
        }

        @Test
        @DisplayName("성공: 첫 환전 (UserExchangeAnnualUsage 없는 경우)")
        void success_firstExchange() {
            when(userExchangeAnnualUsageRepository.findByUserIdAndYear(1L, LocalDate.now().getYear()))
                    .thenReturn(Optional.empty());

            assertThatCode(() -> validator.validateAnnualExchange(user, new BigDecimal("3000")))
                    .doesNotThrowAnyException();
        }
    }
}