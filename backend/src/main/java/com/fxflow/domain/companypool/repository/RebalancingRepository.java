package com.fxflow.domain.companypool.repository;

import com.fxflow.domain.companypool.entity.RebalancingOrder;
import com.fxflow.domain.companypool.enums.RebalancingStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RebalancingRepository extends JpaRepository<RebalancingOrder, Long> {

    // 내역 조회 — 최신순 전체
    List<RebalancingOrder> findAllByOrderByCreatedAtDesc();

    // 스케줄러 재시도 대상 조회 - FAILED 상태만
    List<RebalancingOrder> findAllByStatus(RebalancingStatus status);

    // 멱등성 체크 — 동일 키로 중복 실행 방지
    boolean existsByIdempotencyKey(String idempotencyKey);
}