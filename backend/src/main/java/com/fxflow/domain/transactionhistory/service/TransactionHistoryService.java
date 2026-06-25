package com.fxflow.domain.transactionhistory.service;

import com.fxflow.domain.ledger.entity.LedgerEntry;
import com.fxflow.domain.ledger.enums.LedgerDirection;
import com.fxflow.domain.ledger.enums.LedgerEntryType;
import com.fxflow.domain.ledger.enums.LedgerRefType;
import com.fxflow.domain.ledger.repository.LedgerEntryRepository;
import com.fxflow.domain.mockbankaccount.entity.MockBankAccount;
import com.fxflow.domain.mockbankaccount.repository.MockBankAccountRepository;
import com.fxflow.domain.remittancetransaction.entity.RemittanceTransaction;
import com.fxflow.domain.remittancetransaction.repository.RemittanceTransactionRepository;
import com.fxflow.domain.transactionhistory.dto.response.TransactionHistoryItemResponse;
import com.fxflow.domain.transactionhistory.dto.response.UnifiedTransactionHistoryResponse;
import com.fxflow.domain.wallet.entity.ExchangeTransaction;
import com.fxflow.domain.wallet.entity.P2pTransfer;
import com.fxflow.domain.wallet.entity.Wallet;
import com.fxflow.domain.wallet.repository.ExchangeTransactionRepository;
import com.fxflow.domain.wallet.repository.P2pTransferRepository;
import com.fxflow.domain.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TransactionHistoryService {

    private static final String KRW = "KRW";
    private static final long MISSING_MOCK_ACCOUNT_ID = -1L;

    private final WalletRepository walletRepository;
    private final MockBankAccountRepository mockBankAccountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final ExchangeTransactionRepository exchangeTransactionRepository;
    private final P2pTransferRepository p2pTransferRepository;
    private final RemittanceTransactionRepository remittanceTransactionRepository;

    /**
     * 사용자 거래내역 화면용 조회 메서드다.
     * 지갑 거래는 walletId 기준으로, 해외송금은 송금자 모의계좌 출금 LedgerEntry 기준으로 합친다.
     */
    @Transactional(readOnly = true)
    public UnifiedTransactionHistoryResponse getTransactionHistory(
            Long userId,
            String currency,
            LedgerEntryType type,
            LocalDate from,
            LocalDate to,
            Pageable pageable
    ) {
        List<Long> walletIds = walletRepository.findByUserId(userId)
                .stream()
                .filter(wallet -> currency == null || currency.equals(wallet.getCurrencyCode()))
                .map(Wallet::getId)
                .toList();

        Long mockBankAccountId = mockBankAccountRepository
                .findFirstByUser_IdAndCurrencyCodeAndDeletedAtIsNull(userId, KRW)
                .map(MockBankAccount::getId)
                .orElse(MISSING_MOCK_ACCOUNT_ID);

        LocalDateTime fromDateTime = from != null ? from.atStartOfDay() : null;
        LocalDateTime toDateTime = to != null ? to.atTime(23, 59, 59) : null;

        // 해외송금은 원화풀/외화풀/수취인 입금까지 여러 LedgerEntry가 생기므로,
        // 사용자 목록에는 송금자 모의계좌에서 빠져나간 DEBIT 한 줄만 포함한다.
        Page<LedgerEntry> entries = ledgerEntryRepository.findUnifiedTransactionHistory(
                toInClauseIds(walletIds),
                mockBankAccountId,
                LedgerRefType.REMITTANCE,
                LedgerDirection.DEBIT,
                currency,
                type,
                fromDateTime,
                toDateTime,
                pageable
        );

        Map<String, ExchangeTransaction> exchangeMap = getExchangeMap(entries.getContent());
        Map<String, P2pTransfer> p2pTransferMap = getP2pTransferMap(entries.getContent());
        Map<String, RemittanceTransaction> remittanceMap = getRemittanceMap(entries.getContent());

        Page<TransactionHistoryItemResponse> responsePage = entries.map(entry ->
                mapEntry(entry, exchangeMap, p2pTransferMap, remittanceMap)
        );

        return UnifiedTransactionHistoryResponse.from(responsePage);
    }

    private TransactionHistoryItemResponse mapEntry(
            LedgerEntry entry,
            Map<String, ExchangeTransaction> exchangeMap,
            Map<String, P2pTransfer> p2pTransferMap,
            Map<String, RemittanceTransaction> remittanceMap
    ) {
        // LedgerEntry가 기준이고, 각 업무 테이블은 화면 표시용 상세 정보만 보강한다.
        if (entry.getRefType() == LedgerRefType.REMITTANCE) {
            return TransactionHistoryItemResponse.remittance(entry, remittanceMap.get(entry.getRefId()));
        }
        if (entry.getEntryType() == LedgerEntryType.EXCHANGE) {
            return TransactionHistoryItemResponse.exchange(entry, exchangeMap.get(entry.getRefId()));
        }
        if (entry.getRefType() == LedgerRefType.P2P_TRANSFER) {
            return TransactionHistoryItemResponse.p2pTransfer(entry, p2pTransferMap.get(entry.getRefId()));
        }
        return TransactionHistoryItemResponse.simple(entry);
    }

    private Map<String, ExchangeTransaction> getExchangeMap(List<LedgerEntry> entries) {
        List<String> refIds = entries.stream()
                .filter(entry -> entry.getEntryType() == LedgerEntryType.EXCHANGE)
                .map(LedgerEntry::getRefId)
                .toList();

        if (refIds.isEmpty()) {
            return new HashMap<>();
        }

        return exchangeTransactionRepository.findAllByTransactionIdIn(refIds)
                .stream()
                .collect(Collectors.toMap(ExchangeTransaction::getTransactionId, exchange -> exchange));
    }

    private Map<String, P2pTransfer> getP2pTransferMap(List<LedgerEntry> entries) {
        List<String> refIds = entries.stream()
                .filter(entry -> entry.getRefType() == LedgerRefType.P2P_TRANSFER)
                .map(LedgerEntry::getRefId)
                .toList();

        if (refIds.isEmpty()) {
            return new HashMap<>();
        }

        return p2pTransferRepository.findAllWithUsersByIdIn(refIds)
                .stream()
                .collect(Collectors.toMap(P2pTransfer::getTransferId, transfer -> transfer));
    }

    private Map<String, RemittanceTransaction> getRemittanceMap(List<LedgerEntry> entries) {
        List<String> journalIds = entries.stream()
                .filter(entry -> entry.getRefType() == LedgerRefType.REMITTANCE)
                .map(LedgerEntry::getRefId)
                .toList();

        if (journalIds.isEmpty()) {
            return new HashMap<>();
        }

        return remittanceTransactionRepository.findAllByJournalIdIn(journalIds)
                .stream()
                .collect(Collectors.toMap(RemittanceTransaction::getJournalId, remittance -> remittance));
    }

    private List<Long> toInClauseIds(List<Long> ids) {
        // JPQL IN 절에는 빈 리스트를 넘기지 않기 위해 실제로 존재하지 않는 id를 사용한다.
        if (ids.isEmpty()) {
            return new ArrayList<>(List.of(-1L));
        }
        return ids;
    }
}
