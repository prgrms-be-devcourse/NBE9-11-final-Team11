package com.fxflow.domain.wallet.repository;

import com.fxflow.domain.wallet.entity.P2pTransfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface P2pTransferRepository extends JpaRepository<P2pTransfer, Long> {
    // N+1 방지를 위해 fetch join 사용
    // transactionId(또는 refId) 목록으로 P2pTransfer를 가져올 때, 양쪽 Wallet과 User까지 한 번에 로드합니다.
    @Query("SELECT p FROM P2pTransfer p " +
            "JOIN FETCH p.fromWallet fw JOIN FETCH fw.user " +
            "JOIN FETCH p.toWallet tw JOIN FETCH tw.user " +
            "WHERE p.transferId IN :transferIds")
    List<P2pTransfer> findAllWithUsersByIdIn(@Param("transferIds") List<String> transferIds);
}