package com.fxflow.domain.userlimitusage.repository;

import com.fxflow.domain.userlimitusage.entity.UserExchangeAnnualUsage;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserExchangeAnnualUsageRepository extends JpaRepository<UserExchangeAnnualUsage, Long> {

    Optional<UserExchangeAnnualUsage> findByUserIdAndYear(Long userId, Integer year);

    /**
     * 환전 시 연간 한도 사용량을 갱신하기 위해 사용한다.
     *
     * 같은 유저가 동시에 환전 요청을 보내면 annual_exchange_used_usd를 동시에 읽고
     * 각각 한도 검증을 통과할 수 있으므로, 해당 유저/연도의 사용량 row를
     * PESSIMISTIC_WRITE로 잠근 뒤 검증과 누적 반영을 같은 트랜잭션에서 처리한다. = 비관적 락
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select u
            from UserExchangeAnnualUsage u
            where u.user.id = :userId
              and u.year = :year
            """)
    Optional<UserExchangeAnnualUsage> findByUserIdAndYearForUpdate(
            @Param("userId") Long userId,
            @Param("year") Integer year
    );
}