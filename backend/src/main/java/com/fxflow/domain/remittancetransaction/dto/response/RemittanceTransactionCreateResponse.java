package com.fxflow.domain.remittancetransaction.dto.response;

import com.fxflow.domain.remittancetransaction.entity.RemittanceTransaction;
import com.fxflow.domain.remittancetransaction.entity.VirtualAccount;
import com.fxflow.domain.remittancetransaction.enums.TransferStatus;
import com.fxflow.global.util.CurrencyAmountFormatter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RemittanceTransactionCreateResponse(
        Long transferId,
        TransferStatus status,
        VirtualAccountResponse virtualAccount
) {

    private static final String KRW = "KRW";

    public static RemittanceTransactionCreateResponse of(
            RemittanceTransaction remittanceTransaction,
            VirtualAccount virtualAccount
    ) {
        return new RemittanceTransactionCreateResponse(
                remittanceTransaction.getId(),
                remittanceTransaction.getStatus(),
                VirtualAccountResponse.from(virtualAccount)
        );
    }

    public record VirtualAccountResponse(
            String bankName,
            String accountNumber,
            BigDecimal amount,
            LocalDateTime expiredAt
    ) {

        public static VirtualAccountResponse from(VirtualAccount virtualAccount) {
            return new VirtualAccountResponse(
                    virtualAccount.getBankName(),
                    virtualAccount.getAccountNumber(),
                    CurrencyAmountFormatter.format(virtualAccount.getExpectedAmount(), KRW),
                    virtualAccount.getExpiredAt()
            );
        }
    }
}
