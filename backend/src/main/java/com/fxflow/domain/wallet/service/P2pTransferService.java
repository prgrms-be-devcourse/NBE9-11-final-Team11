package com.fxflow.domain.wallet.service;

import com.fxflow.domain.ledger.entity.LedgerEntry;
import com.fxflow.domain.ledger.enums.LedgerDirection;
import com.fxflow.domain.ledger.enums.LedgerEntryType;
import com.fxflow.domain.ledger.enums.LedgerRefType;
import com.fxflow.domain.ledger.repository.LedgerEntryRepository;
import com.fxflow.domain.transactionlimit.validator.TransactionLimitValidator;
import com.fxflow.domain.user.entity.User;
import com.fxflow.domain.user.service.UserService;
import com.fxflow.domain.wallet.dto.request.P2pTransferRequest;
import com.fxflow.domain.wallet.dto.response.P2pTransferResponse;
import com.fxflow.domain.wallet.entity.P2pTransfer;
import com.fxflow.domain.wallet.entity.Wallet;
import com.fxflow.domain.wallet.errorcode.P2pTransferErrorCode;
import com.fxflow.domain.wallet.errorcode.WalletErrorCode;
import com.fxflow.domain.wallet.repository.P2pTransferRepository;
import com.fxflow.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class P2pTransferService {

    private final WalletService walletService;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final P2pTransferRepository p2pTransferRepository;
    private final UserService userService;
    private final TransactionLimitValidator transactionLimitValidator;

    @Transactional
    public P2pTransferResponse transfer(Long userId, P2pTransferRequest request) {
        // 존재하는 이메일인지 확인
        String recipientEmail = request.recipientEmail();
        User recipient = userService.getUserByEmail(recipientEmail);
        Long recipientId = recipient.getId();

        // 자기 자신의 이메일로 송금 시도 시 에러
        if (recipientId.equals(userId)) {
            throw new BusinessException(P2pTransferErrorCode.SELF_TRANSFER_NOT_ALLOWED);
        }

        // 본인 월렛 잔액 검증
        Wallet senderWallet = walletService.getWallet(userId, request.currency());
        BigDecimal amount = request.amount();
        if (senderWallet.getBalance().compareTo(amount) < 0) {
            throw new BusinessException(WalletErrorCode.INSUFFICIENT_BALANCE);
        }

        // 상대 월렛 한도 검증
        Wallet recipientWallet = walletService.getWallet(recipient.getId(), request.currency());
        transactionLimitValidator.validateWalletHolding(recipient, recipientWallet.getBalance().add(amount));

        // 양쪽 wallet balance 업데이트
        BigDecimal senderBeforeBalance = senderWallet.getBalance();
        BigDecimal recipientBeforeBalance = recipientWallet.getBalance();
        BigDecimal senderAfterBalance = senderWallet.withdraw(amount);
        BigDecimal recipientAfterBalance = recipientWallet.deposit(amount);

        // p2p transfer 기록
        String transferId = P2pTransfer.generateTransferId();
        P2pTransfer transfer = P2pTransfer.create(
                transferId,
                senderWallet,
                recipientWallet,
                request.currency(),
                amount,
                request.memo()
        );
        p2pTransferRepository.save(transfer);

        // ledger transaction 기록
        String journalId = LedgerEntry.generateJournalId();
        LedgerEntry debit = LedgerEntry.create(
                journalId,
                LedgerEntryType.TRANSFER,
                LedgerDirection.DEBIT,
                senderWallet.getId(),
                null,
                null,
                request.currency(),
                amount,
                senderBeforeBalance,
                senderAfterBalance,
                LedgerRefType.P2P_TRANSFER,
                transferId
        );
        LedgerEntry credit = LedgerEntry.create(
                journalId,
                LedgerEntryType.TRANSFER,
                LedgerDirection.CREDIT,
                recipientWallet.getId(),
                null,
                null,
                request.currency(),
                amount,
                recipientBeforeBalance,
                recipientAfterBalance,
                LedgerRefType.P2P_TRANSFER,
                transferId
        );
        ledgerEntryRepository.saveAll(List.of(debit, credit));

        return P2pTransferResponse.create(
                transferId,
                request.recipientEmail(),
                amount,
                request.currency(),
                senderWallet.getBalance()
        );
    }
}