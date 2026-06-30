package com.fxflow.domain.remittancetransaction.repository;

import com.fxflow.domain.remittancetransaction.entity.VirtualAccount;
import com.fxflow.domain.remittancetransaction.enums.VirtualAccountStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface VirtualAccountRepository extends JpaRepository<VirtualAccount, Long> {

    // 고유한 가상계좌 번호로 가상계좌 엔티티를 단건 조회하는 쿼리 메서드
    Optional<VirtualAccount> findByAccountNumber(String accountNumber);

    // 송금 거래 ID로 연결된 가상계좌를 조회하는 쿼리 메서드
    Optional<VirtualAccount> findByRemittanceTransactionId(Long remittanceTransactionId);

    /**
     * 입금 기한이 지난 발급 상태 가상계좌를 잠금 조회한다.
     * 스케줄러와 입금 확인 요청이 동시에 같은 계좌를 변경하지 못하게 하기 위함이다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<VirtualAccount> findByStatusAndExpiredAtLessThanEqual(
            VirtualAccountStatus status,
            LocalDateTime expiredAt
    );
}
