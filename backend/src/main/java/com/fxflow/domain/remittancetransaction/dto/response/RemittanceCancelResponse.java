package com.fxflow.domain.remittancetransaction.dto.response;

import com.fxflow.domain.remittancetransaction.entity.RemittanceTransaction;
import com.fxflow.domain.remittancetransaction.entity.VirtualAccount;
import com.fxflow.domain.remittancetransaction.enums.TransferStatus;
import com.fxflow.domain.remittancetransaction.enums.VirtualAccountStatus;

public record RemittanceCancelResponse(
        Long transferId,
        TransferStatus status,
        VirtualAccountStatus virtualAccountStatus
) {

    public static RemittanceCancelResponse of(
            RemittanceTransaction remittanceTransaction,
            VirtualAccount virtualAccount
    ) {
        return new RemittanceCancelResponse(
                remittanceTransaction.getId(),
                remittanceTransaction.getStatus(),
                virtualAccount.getStatus()
        );
    }
}
