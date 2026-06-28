package com.fxflow.domain.userlimitusage.repository;

import com.fxflow.domain.userlimitusage.entity.UserExchangeDailyUsage;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface UserExchangeDailyUsageRepository extends JpaRepository<UserExchangeDailyUsage, Long> {

    Optional<UserExchangeDailyUsage> findByUserIdAndUsageDate(Long userId, LocalDate usageDate);

    /**
     * 환전 시 일일 한도 사용량을 갱신하기 위해 사용한다.
     *
     * 같은 유저가 동시에 환전 요청을 보내면 daily_exchange_used를 동시에 읽고
     * 각각 한도 검증을 통과할 수 있으므로, 해당 유저/날짜의 사용량 row를
     * PESSIMISTIC_WRITE로 잠근 뒤 검증과 누적 반영을 같은 트랜잭션에서 처리한다. = 비관적 락
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select u
            from UserExchangeDailyUsage u
            where u.user.id = :userId
              and u.usageDate = :usageDate
            """)
    Optional<UserExchangeDailyUsage> findByUserIdAndUsageDateForUpdate(
            @Param("userId") Long userId,
            @Param("usageDate") LocalDate usageDate
    );
}