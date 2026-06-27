package com.fxflow.domain.mockbankaccount.repository;

import com.fxflow.domain.mockbankaccount.entity.KycVerification;
import com.fxflow.domain.mockbankaccount.enums.KycVerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface KycVerificationRepository extends JpaRepository<KycVerification, Long> {

    Optional<KycVerification> findByIdAndUserId(Long id, Long userId);

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
