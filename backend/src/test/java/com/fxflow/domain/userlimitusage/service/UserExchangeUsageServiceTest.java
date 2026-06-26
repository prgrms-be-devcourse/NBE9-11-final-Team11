package com.fxflow.domain.userlimitusage.service;

import com.fxflow.domain.user.entity.User;
import com.fxflow.domain.user.repository.UserRepository;
import com.fxflow.domain.userlimitusage.entity.UserExchangeAnnualUsage;
import com.fxflow.domain.userlimitusage.entity.UserExchangeDailyUsage;
import com.fxflow.domain.userlimitusage.repository.UserExchangeAnnualUsageRepository;
import com.fxflow.domain.userlimitusage.repository.UserExchangeDailyUsageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserExchangeUsageServiceTest {

    @Mock
    private UserExchangeDailyUsageRepository userExchangeDailyUsageRepository;
    @Mock
    private UserExchangeAnnualUsageRepository userExchangeAnnualUsageRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserExchangeUsageService userExchangeUsageService;

    private Long userId;
    private User user;

    @BeforeEach
    void setUp() {
        userId = 1L;
        user = User.create("email", "password", "name");
        ReflectionTestUtils.setField(user, "id", userId);
    }

    @Test
    @DisplayName("기존 일일 환전 사용량이 있으면 누적해서 저장한다")
    void addDailyExchange_accumulatesOnExistingUsage() {
        // given
        LocalDate today = LocalDate.of(2026, 6, 26);
        UserExchangeDailyUsage existing = UserExchangeDailyUsage.create(user, today);
        existing.addExchange(new BigDecimal("100000")); // 기존에 10만원 사용

        given(userExchangeDailyUsageRepository.findByUserIdAndUsageDateForUpdate(userId, today))
                .willReturn(Optional.of(existing));

        // when
        userExchangeUsageService.addDailyExchange(userId, today, new BigDecimal("500000")); // 50만원 추가

        // then
        ArgumentCaptor<UserExchangeDailyUsage> captor = ArgumentCaptor.forClass(UserExchangeDailyUsage.class);
        verify(userExchangeDailyUsageRepository).save(captor.capture());
        assertThat(captor.getValue().getDailyExchangeUsed()).isEqualByComparingTo(new BigDecimal("600000"));
    }

    @Test
    @DisplayName("일일 환전 사용량이 없으면 새로 생성해서 저장한다")
    void addDailyExchange_createsWhenAbsent() {
        // given
        LocalDate today = LocalDate.of(2026, 6, 26);
        given(userExchangeDailyUsageRepository.findByUserIdAndUsageDateForUpdate(userId, today))
                .willReturn(Optional.empty());
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        // when
        userExchangeUsageService.addDailyExchange(userId, today, new BigDecimal("300000"));

        // then
        ArgumentCaptor<UserExchangeDailyUsage> captor = ArgumentCaptor.forClass(UserExchangeDailyUsage.class);
        verify(userExchangeDailyUsageRepository).save(captor.capture());
        assertThat(captor.getValue().getDailyExchangeUsed()).isEqualByComparingTo(new BigDecimal("300000"));
        assertThat(captor.getValue().getUsageDate()).isEqualTo(today);
    }

    @Test
    @DisplayName("같은 날짜에 환전이 반복되면 누적값이 계속 더해진다")
    void addDailyExchange_calledTwice_accumulatesAcrossCalls() {
        // given: 같은 row 객체를 두 번 조회한 것처럼 시뮬레이션 (비관적 락으로 직렬화된 상황 가정)
        LocalDate today = LocalDate.of(2026, 6, 26);
        UserExchangeDailyUsage usage = UserExchangeDailyUsage.create(user, today);
        given(userExchangeDailyUsageRepository.findByUserIdAndUsageDateForUpdate(userId, today))
                .willReturn(Optional.of(usage));

        // when
        userExchangeUsageService.addDailyExchange(userId, today, new BigDecimal("200000"));
        userExchangeUsageService.addDailyExchange(userId, today, new BigDecimal("150000"));

        // then
        assertThat(usage.getDailyExchangeUsed()).isEqualByComparingTo(new BigDecimal("350000"));
    }

    @Test
    @DisplayName("기존 연간 환전 사용량이 있으면 USD 기준으로 누적해서 저장한다")
    void addAnnualExchange_accumulatesOnExistingUsage() {
        // given
        int year = 2026;
        UserExchangeAnnualUsage existing = UserExchangeAnnualUsage.create(user, year);
        existing.addExchange(new BigDecimal("1000")); // 기존에 $1000 사용

        given(userExchangeAnnualUsageRepository.findByUserIdAndYearForUpdate(userId, year))
                .willReturn(Optional.of(existing));

        // when
        userExchangeUsageService.addAnnualExchange(userId, year, new BigDecimal("370.12"));

        // then
        ArgumentCaptor<UserExchangeAnnualUsage> captor = ArgumentCaptor.forClass(UserExchangeAnnualUsage.class);
        verify(userExchangeAnnualUsageRepository).save(captor.capture());
        assertThat(captor.getValue().getAnnualExchangeUsedUsd()).isEqualByComparingTo(new BigDecimal("1370.12"));
    }

    @Test
    @DisplayName("연간 환전 사용량이 없으면 새로 생성해서 저장한다")
    void addAnnualExchange_createsWhenAbsent() {
        // given
        int year = 2026;
        given(userExchangeAnnualUsageRepository.findByUserIdAndYearForUpdate(userId, year))
                .willReturn(Optional.empty());
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        // when
        userExchangeUsageService.addAnnualExchange(userId, year, new BigDecimal("370.12"));

        // then
        ArgumentCaptor<UserExchangeAnnualUsage> captor = ArgumentCaptor.forClass(UserExchangeAnnualUsage.class);
        verify(userExchangeAnnualUsageRepository).save(captor.capture());
        assertThat(captor.getValue().getAnnualExchangeUsedUsd()).isEqualByComparingTo(new BigDecimal("370.12"));
        assertThat(captor.getValue().getYear()).isEqualTo(year);
    }
}
