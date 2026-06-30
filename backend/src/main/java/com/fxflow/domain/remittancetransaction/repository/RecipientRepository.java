package com.fxflow.domain.remittancetransaction.repository;

import com.fxflow.domain.remittancetransaction.entity.Recipient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RecipientRepository extends JpaRepository<Recipient, Long> {

    /**
     * 특정 사용자가 등록한 수취인 목록을 최신순으로 조회한다.
     * Soft Delete 처리된 수취인은 조회 대상에서 제외한다.
     */
    List<Recipient> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long userId);

    /**
     * 특정 사용자가 동일한 해외 수취 계좌를 이미 등록했는지 확인한다.
     * 같은 국가, 은행명, 계좌번호 조합이 존재하면 중복 수취인으로 판단한다.
     * Soft Delete 처리된 수취인은 중복 검사 대상에서 제외한다.
     */
    boolean existsByUserIdAndCountryCodeAndBankNameAndAccountNumberAndDeletedAtIsNull(
            Long userId,
            String countryCode,
            String bankName,
            String accountNumber
    );

    /**
     * 특정 수취인이 로그인한 사용자의 수취인인지 확인한다.
     * Soft Delete 처리된 수취인은 유효하지 않은 수취인으로 판단한다.
     */
    boolean existsByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);

    /**
     * 송금 주문 생성 시 사용할 수취인을 조회한다.
     * 송금 당시 수취인 정보를 거래에 스냅샷으로 저장하기 위해 사용한다.
     */
    Optional<Recipient> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);
}