package com.fxflow.domain.reservation.dto.request;

import com.fxflow.domain.remittancetransaction.enums.RemittanceReason;
import com.fxflow.domain.reservation.enums.ReservationAction;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 예약 생성 요청 (RSV-01).
 * 항상 필요한 값은 필드 검증으로, 동작(EXCHANGE/REMITTANCE)별 조건부 필수값은 엔티티 팩토리에서 검증한다.
 */
public record ReservationCreateRequest(

        @NotNull(message = "예약 동작은 필수입니다.")
        ReservationAction action,                 // EXCHANGE / REMITTANCE

        @NotBlank(message = "출금 통화는 필수입니다.")
        @Size(min = 3, max = 3, message = "통화 코드는 3자리여야 합니다.")
        String fromCurrency,

        @NotBlank(message = "입금 통화는 필수입니다.")
        @Size(min = 3, max = 3, message = "통화 코드는 3자리여야 합니다.")
        String toCurrency,

        @NotNull(message = "예약 금액은 필수입니다.")
        @Positive(message = "예약 금액은 0보다 커야 합니다.")
        BigDecimal amount,

        @NotNull(message = "목표 환율은 필수입니다.")
        @Positive(message = "목표 환율은 0보다 커야 합니다.")
        BigDecimal targetRate,

        @NotNull(message = "만료 시각은 필수입니다.")
        @Future(message = "만료 시각은 현재 이후여야 합니다.")
        LocalDateTime expiresAt,

        // ── 송금(REMITTANCE) 전용 — 동작에 따라 서비스/엔티티에서 조건부 검증 ──
        Long recipientId,                         // 수취인

        RemittanceReason remittanceReason,        // 송금 사유

        @Size(max = 255, message = "송금 사유 상세는 255자 이하여야 합니다.")
        String remittanceReasonDetail
) {
}
