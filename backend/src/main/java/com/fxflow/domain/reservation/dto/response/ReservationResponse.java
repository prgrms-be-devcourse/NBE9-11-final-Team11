package com.fxflow.domain.reservation.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fxflow.domain.remittancetransaction.enums.RemittanceReason;
import com.fxflow.domain.reservation.entity.Reservation;
import com.fxflow.domain.reservation.enums.ReservationAction;
import com.fxflow.domain.reservation.enums.ReservationStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 예약 단건 응답 (생성·단건 조회·취소·목록 항목 공용).
 * 내부 관리 필드(idempotencyKey, version)는 노출하지 않으며, null 필드는 응답에서 생략
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReservationResponse(
        Long reservationId,
        ReservationAction action,
        ReservationStatus status,
        String fromCurrency,
        String toCurrency,
        BigDecimal amount,
        BigDecimal targetRate,
        LocalDateTime expiresAt,
        Long recipientId,
        RemittanceReason remittanceReason,
        String remittanceReasonDetail,
        LocalDateTime triggeredAt,
        LocalDateTime executedAt,
        Long resultExchangeTransactionId,
        Long resultRemittanceTransactionId,
        String failureReason,
        LocalDateTime createdAt
) {
    public static ReservationResponse from(Reservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getAction(),
                reservation.getStatus(),
                reservation.getFromCurrency(),
                reservation.getToCurrency(),
                reservation.getAmount(),
                reservation.getTargetRate(),
                reservation.getExpiresAt(),
                reservation.getRecipientId(),
                reservation.getRemittanceReason(),
                reservation.getRemittanceReasonDetail(),
                reservation.getTriggeredAt(),
                reservation.getExecutedAt(),
                reservation.getResultExchangeTransactionId(),
                reservation.getResultRemittanceTransactionId(),
                reservation.getFailureReason(),
                reservation.getCreatedAt()
        );
    }
}
