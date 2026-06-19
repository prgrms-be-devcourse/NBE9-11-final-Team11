package com.fxflow.domain.wallet.repository;

import com.fxflow.domain.remittancetransaction.enums.TransferStatus;
import com.fxflow.domain.wallet.entity.P2pTransfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface P2pTransferRepository extends JpaRepository<P2pTransfer, Long> {
}