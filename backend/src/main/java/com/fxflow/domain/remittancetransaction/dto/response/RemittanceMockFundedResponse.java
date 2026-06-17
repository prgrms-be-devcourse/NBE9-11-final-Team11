package com.fxflow.domain.remittancetransaction.dto.response;

import com.fxflow.domain.remittancetransaction.entity.RemittanceTransaction;
import com.fxflow.domain.remittancetransaction.entity.VirtualAccount;
import com.fxflow.domain.remittancetransaction.enums.TransferStatus;
import com.fxflow.domain.remittancetransaction.enums.VirtualAccountStatus;

import java.time.LocalDateTime;

public record RemittanceMockFundedResponse(
        Long transferId,
        TransferStatus status,
        VirtualAccountStatus virtualAccountStatus,
        LocalDateTime fundedAt
) {

    public static RemittanceMockFundedResponse of(
            RemittanceTransaction remittanceTransaction,
            VirtualAccount virtualAccount
    ) {
        return new RemittanceMockFundedResponse(
                remittanceTransaction.getId(),
                remittanceTransaction.getStatus(),
                virtualAccount.getStatus(),
                virtualAccount.getPaidAt()
        );
    }
}