package com.fxflow.domain.userlimitusage.repository;

import com.fxflow.domain.userlimitusage.entity.UserAnnualUsage;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;

public interface UserAnnualUsageRepository extends JpaRepository<UserAnnualUsage, Long> {

    Optional<UserAnnualUsage> findByUserIdAndYear(Long userId, Integer year);

    /**
     * 송금 주문 생성 시 연간 송금 한도를 선점하기 위해 사용한다.
     *
     * 같은 유저가 동시에 여러 송금 주문을 생성하면 annual_used_usd를 동시에 읽고
     * 각각 한도 검증을 통과할 수 있으므로, 해당 유저/연도의 사용량 row를
     * PESSIMISTIC_WRITE로 잠근 뒤 검증과 누적 반영을 같은 트랜잭션에서 처리한다. = 비관적 락
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select u
            from UserAnnualUsage u
            where u.user.id = :userId
              and u.year = :year
            """)
    Optional<UserAnnualUsage> findByUserIdAndYearForUpdate(
            @Param("userId") Long userId,
            @Param("year") Integer year
    );

    /**
     * 최초 송금 요청이 동시에 들어와도 user_id + usage_year row가 하나만 만들어지도록 보장한다.
     */
    @Modifying
    @Query(value = """
            insert into user_annual_usages (
                user_id,
                usage_year,
                annual_used_usd,
                version,
                created_at,
                updated_at
            )
            values (
                :userId,
                :year,
                0,
                0,
                current_timestamp,
                current_timestamp
            )
            on conflict (user_id, usage_year) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("userId") Long userId,
            @Param("year") Integer year
    );

    /**
     * 연간 한도 초과 여부와 누적액 증가를 단일 UPDATE로 처리한다.
     */
    @Modifying
    @Query(value = """
            update user_annual_usages
            set annual_used_usd = annual_used_usd + :amountUsd,
                updated_at = current_timestamp
            where user_id = :userId
              and usage_year = :year
              and annual_used_usd + :amountUsd <= :annualLimitUsd
            """, nativeQuery = true)
    int reserveAnnualLimit(
            @Param("userId") Long userId,
            @Param("year") Integer year,
            @Param("amountUsd") BigDecimal amountUsd,
            @Param("annualLimitUsd") BigDecimal annualLimitUsd
    );
}
