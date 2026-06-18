package com.fxflow.domain.remittancetransaction.service;

import com.fxflow.domain.companypool.service.CompanyPoolService;
import com.fxflow.domain.mockbankaccount.service.MockBankAccountService;
import com.fxflow.domain.remittancetransaction.dto.response.RemittanceLimitResponse;
import com.fxflow.domain.remittancetransaction.repository.RecipientRepository;
import com.fxflow.domain.remittancetransaction.repository.RemittanceTransactionRepository;
import com.fxflow.domain.remittancetransaction.repository.VirtualAccountRepository;
import com.fxflow.domain.remittancetransaction.validator.RemittanceValidator;
import com.fxflow.domain.transactionlimit.entity.TransactionLimit;
import com.fxflow.domain.transactionlimit.enums.LimitTier;
import com.fxflow.domain.transactionlimit.enums.LimitType;
import com.fxflow.domain.transactionlimit.errorcode.TransactionLimitErrorCode;
import com.fxflow.domain.transactionlimit.repository.TransactionLimitRepository;
import com.fxflow.domain.user.entity.User;
import com.fxflow.domain.userlimitusage.entity.UserAnnualUsage;
import com.fxflow.domain.userlimitusage.repository.UserAnnualUsageRepository;
import com.fxflow.global.exception.BusinessException;
import com.fxflow.global.fx.ExchangeRateProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RemittanceLimitServiceTest {

    @Mock
    private UserAnnualUsageRepository userAnnualUsageRepository;

    @Mock
    private TransactionLimitRepository transactionLimitRepository;

    @Mock
    private RecipientRepository recipientRepository;

    @Mock
    private RemittanceTransactionRepository remittanceTransactionRepository;

    @Mock
    private VirtualAccountRepository virtualAccountRepository;

    @Mock
    private RemittanceQuoteProvider remittanceQuoteProvider;

    @Mock
    private RemittanceValidator remittanceValidator;

    @Mock
    private MockBankAccountService mockBankAccountService;

    @Mock
    private CompanyPoolService companyPoolService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private ExchangeRateProvider exchangeRateProvider;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @InjectMocks
    private RemittanceTransactionService remittanceTransactionService;

    @Test
    @DisplayName("성공: 사용자의 해외송금 잔여 한도를 조회한다")
    void getRemittanceLimit_success() {
        Long userId = 1L;
        int currentYear = LocalDate.now(ZoneId.of("Asia/Seoul")).getYear();
        UserAnnualUsage usage = createAnnualUsage(userId, currentYear, new BigDecimal("5200.00000000"));

        when(userAnnualUsageRepository.findByUserIdAndYear(userId, currentYear))
                .thenReturn(Optional.of(usage));
        when(transactionLimitRepository.findByLimitTypeAndTierAndCurrencyCodeAndIsActiveTrue(
                LimitType.PER_REMITTANCE,
                LimitTier.STANDARD,
                "USD"
        )).thenReturn(Optional.of(createLimit(
                LimitType.PER_REMITTANCE,
                new BigDecimal("5000.00000000")
        )));
        when(transactionLimitRepository.findByLimitTypeAndTierAndCurrencyCodeAndIsActiveTrue(
                LimitType.ANNUAL_REMITTANCE,
                LimitTier.STANDARD,
                "USD"
        )).thenReturn(Optional.of(createLimit(
                LimitType.ANNUAL_REMITTANCE,
                new BigDecimal("100000.00000000")
        )));

        RemittanceLimitResponse response = remittanceTransactionService.getRemittanceLimit(userId);

        assertThat(response.perTransferLimitUsd()).isEqualByComparingTo(new BigDecimal("5000.00"));
        assertThat(response.annualLimitUsd()).isEqualByComparingTo(new BigDecimal("100000.00"));
        assertThat(response.usedUsd()).isEqualByComparingTo(new BigDecimal("5200.00"));
        assertThat(response.remainingUsd()).isEqualByComparingTo(new BigDecimal("94800.00"));
    }

    @Test
    @DisplayName("성공: 연간 사용량이 없으면 0달러 사용 기준으로 잔여 한도를 조회한다")
    void getRemittanceLimit_success_withoutUsage() {
        Long userId = 1L;
        int currentYear = LocalDate.now(ZoneId.of("Asia/Seoul")).getYear();

        when(userAnnualUsageRepository.findByUserIdAndYear(userId, currentYear))
                .thenReturn(Optional.empty());
        when(transactionLimitRepository.findByLimitTypeAndTierAndCurrencyCodeAndIsActiveTrue(
                LimitType.PER_REMITTANCE,
                LimitTier.STANDARD,
                "USD"
        )).thenReturn(Optional.of(createLimit(
                LimitType.PER_REMITTANCE,
                new BigDecimal("5000.00000000")
        )));
        when(transactionLimitRepository.findByLimitTypeAndTierAndCurrencyCodeAndIsActiveTrue(
                LimitType.ANNUAL_REMITTANCE,
                LimitTier.STANDARD,
                "USD"
        )).thenReturn(Optional.of(createLimit(
                LimitType.ANNUAL_REMITTANCE,
                new BigDecimal("100000.00000000")
        )));

        RemittanceLimitResponse response = remittanceTransactionService.getRemittanceLimit(userId);

        assertThat(response.perTransferLimitUsd()).isEqualByComparingTo(new BigDecimal("5000.00"));
        assertThat(response.annualLimitUsd()).isEqualByComparingTo(new BigDecimal("100000.00"));
        assertThat(response.usedUsd()).isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(response.remainingUsd()).isEqualByComparingTo(new BigDecimal("100000.00"));
    }

    @Test
    @DisplayName("실패: 송금 한도 정책이 없으면 예외가 발생한다")
    void getRemittanceLimit_fail_limitPolicyNotFound() {
        Long userId = 1L;
        int currentYear = LocalDate.now(ZoneId.of("Asia/Seoul")).getYear();

        when(userAnnualUsageRepository.findByUserIdAndYear(userId, currentYear))
                .thenReturn(Optional.empty());
        when(transactionLimitRepository.findByLimitTypeAndTierAndCurrencyCodeAndIsActiveTrue(
                LimitType.PER_REMITTANCE,
                LimitTier.STANDARD,
                "USD"
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() -> remittanceTransactionService.getRemittanceLimit(userId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(TransactionLimitErrorCode.LIMIT_POLICY_NOT_FOUND);
    }

    private UserAnnualUsage createAnnualUsage(Long userId, int year, BigDecimal usedUsd) {
        User user = User.create(
                "limit-user@example.com",
                "encoded-password",
                "한도조회사용자"
        );
        ReflectionTestUtils.setField(user, "id", userId);

        UserAnnualUsage usage = UserAnnualUsage.create(user, year);
        usage.addUsage(usedUsd);

        return usage;
    }

    private TransactionLimit createLimit(LimitType limitType, BigDecimal limitAmount) {
        return TransactionLimit.create(
                limitType,
                LimitTier.STANDARD,
                "USD",
                limitAmount
        );
    }
}