package com.fxflow.domain.remittancetransaction.dto.response;

import com.fxflow.domain.remittancetransaction.entity.RemittanceTransaction;
import com.fxflow.domain.remittancetransaction.entity.Recipient;
import com.fxflow.domain.remittancetransaction.enums.TransferStatus;
import com.fxflow.global.util.CurrencyAmountFormatter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RemittanceTransactionSummaryResponse(
        Long transferId,
        String journalId,
        String recipientName,
        String recipientCountryCode,
        String recipientCurrencyCode,
        String recipientBankName,
        BigDecimal sendAmount,
        String sendCurrency,
        BigDecimal receiveAmount,
        String receiveCurrency,
        BigDecimal appliedRate,
        BigDecimal feeAmount,
        TransferStatus status,
        LocalDateTime createdAt
) {

    private static final String KRW = "KRW";

    /**
     * 송금 내역 목록에 필요한 요약 정보를 생성한다.
     * 수취인 정보는 recipientId로 연결된 수취인 주소록에서 조회한다.
     *
     * MVP에서는 수취인 수정 기능을 제공하지 않고 soft delete만 허용하므로,
     * 별도 스냅샷 컬럼 없이 recipientId를 통해 수취인 정보를 조회한다.
     */
    public static RemittanceTransactionSummaryResponse from(
            RemittanceTransaction remittanceTransaction,
            Recipient recipient
    ) {
        return new RemittanceTransactionSummaryResponse(
                remittanceTransaction.getId(),
                remittanceTransaction.getJournalId(),
                recipient.getName(),
                recipient.getCountryCode(),
                recipient.getCurrencyCode(),
                recipient.getBankName(),
                CurrencyAmountFormatter.format(
                        remittanceTransaction.getSendAmount(),
                        remittanceTransaction.getSendCurrency()
                ),
                remittanceTransaction.getSendCurrency(),
                CurrencyAmountFormatter.format(
                        remittanceTransaction.getReceiveAmount(),
                        remittanceTransaction.getReceiveCurrency()
                ),
                remittanceTransaction.getReceiveCurrency(),
                remittanceTransaction.getAppliedRate(),
                CurrencyAmountFormatter.format(remittanceTransaction.getFeeAmount(), KRW),
                remittanceTransaction.getStatus(),
                remittanceTransaction.getCreatedAt()
        );
    }
}
