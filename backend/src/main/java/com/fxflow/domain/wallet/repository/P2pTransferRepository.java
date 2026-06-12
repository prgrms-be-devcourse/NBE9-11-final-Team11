package com.fxflow.domain.wallet.repository;

import com.fxflow.domain.wallet.entity.P2pTransfer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface P2pTransferRepository extends JpaRepository<P2pTransfer, Long> {

}