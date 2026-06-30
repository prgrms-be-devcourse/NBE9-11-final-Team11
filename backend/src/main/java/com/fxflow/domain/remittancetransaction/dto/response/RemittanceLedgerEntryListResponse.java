package com.fxflow.domain.remittancetransaction.dto.response;

import com.fxflow.domain.ledger.entity.LedgerEntry;

import java.util.List;

public record RemittanceLedgerEntryListResponse(
        List<RemittanceLedgerEntryResponse> data
) {

    public static RemittanceLedgerEntryListResponse from(List<LedgerEntry> entries) {
        return new RemittanceLedgerEntryListResponse(
                entries.stream()
                        .map(RemittanceLedgerEntryResponse::from)
                        .toList()
        );
    }
}
