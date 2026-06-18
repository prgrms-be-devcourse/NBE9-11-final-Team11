package com.fxflow.domain.remittancetransaction.dto.response;

import com.fxflow.domain.remittancetransaction.entity.RemittanceTransaction;
import com.fxflow.domain.remittancetransaction.entity.Recipient;
import com.fxflow.domain.remittancetransaction.enums.TransferStatus;
import com.fxflow.global.util.CurrencyAmountFormatter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RemittanceTransactionDetailResponse(
        Long transferId,
        TransferStatus status,
        RecipientInfo recipient,
        BigDecimal sendAmountKrw,
        BigDecimal receiveAmountUsd,
        BigDecimal appliedRate,
        BigDecimal totalFee,
        LocalDateTime createdAt
) {

    private static final String KRW = "KRW";
    private static final String USD = "USD";

    public record RecipientInfo(
            String name,
            String bankName,
            String accountNumber
    ) {

        public static RecipientInfo from(Recipient recipient) {
            return new RecipientInfo(
                    recipient.getName(),
                    recipient.getBankName(),
                    recipient.getAccountNumber()
            );
        }
    }

    /**
     * 송금 상세 조회 응답을 생성한다.
     * 사용자 화면에 필요한 상태, 수취인, 금액, 환율, 수수료, 신청일만 반환한다.
     */
    public static RemittanceTransactionDetailResponse from(
            RemittanceTransaction remittanceTransaction,
            Recipient recipient
    ) {
        return new RemittanceTransactionDetailResponse(
                remittanceTransaction.getId(),
                remittanceTransaction.getStatus(),
                RecipientInfo.from(recipient),
                CurrencyAmountFormatter.format(
                        remittanceTransaction.getSendAmount(),
                        KRW
                ),
                CurrencyAmountFormatter.format(
                        remittanceTransaction.getReceiveAmount(),
                        USD
                ),
                remittanceTransaction.getAppliedRate(),
                CurrencyAmountFormatter.format(remittanceTransaction.getFeeAmount(), KRW),
                remittanceTransaction.getCreatedAt()
        );
    }
}
