package com.fxflow.domain.wallet.repository;

import com.fxflow.domain.remittancetransaction.enums.TransferStatus;
import com.fxflow.domain.wallet.entity.P2pTransfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface P2pTransferRepository extends JpaRepository<P2pTransfer, Long> {
    @Query("""
            SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END
            FROM P2pTransfer p
            WHERE (p.fromWallet.user.id = :userId OR p.toWallet.user.id = :userId)
              AND p.status = :status
            """)
    boolean existsActiveTransferByUserId(
            @Param("userId") Long userId,
            @Param("status") TransferStatus status
    );
}