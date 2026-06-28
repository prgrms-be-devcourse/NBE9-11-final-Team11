package com.fxflow.domain.mockbankaccount.repository;

import com.fxflow.domain.mockbankaccount.entity.KycVerification;
import com.fxflow.domain.mockbankaccount.enums.KycVerificationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface KycVerificationRepository extends JpaRepository<KycVerification, Long> {

    /**
     * 동시에 여러 요청이 같은 인증 건의 코드를 검증하려 할 때 시도 횟수가
     * 레이스 컨디션으로 누락되지 않도록 비관적 쓰기 락을 건다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<KycVerification> findByIdAndUserId(Long id, Long userId);

    /**
     * '다시 요청' 시 이전에 발급된 대기 중인 인증 건들을 무효화하기 위해 조회한다.
     */
    List<KycVerification> findAllByUserIdAndStatus(Long userId, KycVerificationStatus status);

    /**
     * 1원 인증 일일 요청 한도 체크 — 스키마 변경 없이 created_at 기준으로 오늘 발급된 건수를 센다.
     */
    long countByUserIdAndCreatedAtAfter(Long userId, LocalDateTime since);

    /**
     * 사용자가 '계좌번호 조회'로 입금자명(코드)을 확인할 때 사용한다.
     * 입력한 계좌번호/은행명/예금주명이 모두 일치하는 가장 최근 대기 중 인증 건을 찾는다.
     */
    Optional<KycVerification> findFirstByBankNameAndAccountNumberAndAccountHolderNameAndStatusOrderByCreatedAtDesc(
            String bankName,
            String accountNumber,
            String accountHolderName,
            KycVerificationStatus status
    );
}
