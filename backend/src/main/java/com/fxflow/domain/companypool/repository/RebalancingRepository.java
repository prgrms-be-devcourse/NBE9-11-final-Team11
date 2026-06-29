package com.fxflow.domain.companypool.repository;

import com.fxflow.domain.companypool.entity.RebalancingOrder;
import com.fxflow.domain.companypool.enums.RebalancingStatus;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RebalancingRepository extends JpaRepository<RebalancingOrder, Long> {

    // 내역 조회 — 최신순 전체 (테스트용)
    List<RebalancingOrder> findAllByOrderByCreatedAtDesc();

    // 내역 조회 — 최신순 페이지네이션
    Page<RebalancingOrder> findAllByOrderByCreatedAtDesc(Pageable pageable);

    // 멱등성 체크 — 동일 키로 중복 실행 방지
    boolean existsByIdempotencyKey(String idempotencyKey);
}