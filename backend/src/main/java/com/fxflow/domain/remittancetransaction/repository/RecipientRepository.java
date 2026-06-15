package com.fxflow.domain.remittancetransaction.repository;

import com.fxflow.domain.remittancetransaction.entity.Recipient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RecipientRepository extends JpaRepository<Recipient, Long> {

    // 유저 ID를 기준으로 등록된 모든 수취인 주소록을 정렬해서 가져오는 쿼리 메서드 자동 생성
    List<Recipient> findByUserIdOrderByCreatedAtDesc(Long userId);
}