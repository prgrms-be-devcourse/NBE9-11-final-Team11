package com.fxflow.domain.remittancetransaction.dto.response;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fxflow.domain.ledger.entity.LedgerEntry;
import com.fxflow.domain.ledger.enums.LedgerDirection;
import com.fxflow.domain.ledger.enums.LedgerEntryType;
import com.fxflow.domain.ledger.enums.LedgerRefType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 특정 해외송금 한 건에 연결된 LedgerEntry 흐름을 보여주는 응답이다.
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "type",
        visible = true
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = RemittanceLedgerEntryResponse.Transfer.class, name = "TRANSFER")
})
public sealed interface RemittanceLedgerEntryResponse
        permits RemittanceLedgerEntryResponse.Transfer {

    static RemittanceLedgerEntryResponse from(LedgerEntry entry) {
        return Transfer.from(entry);
    }

    record Transfer(
            String journalId,
            LedgerEntryType type,
            LedgerRefType refType,
            LedgerDirection direction,
            String currency,
            BigDecimal amount,
            BigDecimal balanceAfter,
            String ledgerTarget,
            LocalDateTime createdAt
    ) implements RemittanceLedgerEntryResponse {

        public static Transfer from(LedgerEntry entry) {
            return new Transfer(
                    entry.getJournalId(),
                    entry.getEntryType(),
                    entry.getRefType(),
                    entry.getLedgerDirection(),
                    entry.getCurrencyCode(),
                    entry.getAmount(),
                    entry.getBalanceAfter(),
                    resolveLedgerTarget(entry),
                    entry.getCreatedAt()
            );
        }

        private static String resolveLedgerTarget(LedgerEntry entry) {
            if (entry.getMockBankAccountId() != null) {
                return "MOCK_BANK_ACCOUNT";
            }
            if (entry.getCompanyPoolId() != null) {
                return "COMPANY_POOL";
            }
            if (entry.getWalletId() != null) {
                return "WALLET";
            }
            return "UNKNOWN";
        }
    }
}
