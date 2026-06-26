package com.fxflow.domain.reservation.entity;

import com.fxflow.domain.reservation.enums.ReservationAction;
import com.fxflow.domain.reservation.enums.ReservationStatus;
import com.fxflow.domain.reservation.enums.ReservationType;
import com.fxflow.domain.reservation.exception.ReservationErrorCode;
import com.fxflow.domain.remittancetransaction.enums.RemittanceReason;
import com.fxflow.global.entity.BaseEntity;
import com.fxflow.global.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "reservations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation extends BaseEntity {
    // id / createdAt / updatedAt 는 BaseEntity 상속 — 재선언 금지

    @Column(name = "user_id", nullable = false)
    private Long userId;                          // 예약 소유자

    @Enumerated(EnumType.STRING)
    @Column(name = "action", length = 30, nullable = false)
    private ReservationAction action;             // 동작: EXCHANGE / REMITTANCE

    @Column(name = "from_currency", length = 3, nullable = false)
    private String fromCurrency;                  // 내놓는 통화 (amount 기준)

    @Column(name = "to_currency", length = 3, nullable = false)
    private String toCurrency;                    // 받는 통화

    @Column(name = "amount", precision = 18, scale = 8, nullable = false)
    private BigDecimal amount;                    // 예약 금액 (fromCurrency 기준)

    @Column(name = "target_rate", precision = 18, scale = 8, nullable = false)
    private BigDecimal targetRate;                // 목표 환율 (USD/KRW 기준가) — 도달 판정은 서비스에서 방향별로 수행

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 30, nullable = false)
    private ReservationType type;                 // 예약 조건: TARGET_RATE / SCHEDULED

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;              // 만료 시각 (기한 내 미도달 시 EXPIRED)

    @Column(name = "recipient_id")
    private Long recipientId;                     // 수취인 — REMITTANCE 일 때만 (타 도메인 ID)

    @Enumerated(EnumType.STRING)
    @Column(name = "remittance_reason", length = 50)
    private RemittanceReason remittanceReason;    // 송금 사유 — REMITTANCE 일 때만

    @Column(name = "remittance_reason_detail", length = 255)
    private String remittanceReasonDetail;        // 송금 사유 상세 — 선택값

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private ReservationStatus status;             // ACTIVE→TRIGGERED→COMPLETED / CANCELED·EXPIRED·FAILED

    @Column(name = "triggered_at")
    private LocalDateTime triggeredAt;            // 목표 도달 시각

    @Column(name = "executed_at")
    private LocalDateTime executedAt;             // 체결 완료 시각

    @Column(name = "result_exchange_transaction_id")
    private Long resultExchangeTransactionId;     // 체결 결과(EXCHANGE) — 타 도메인 ID

    @Column(name = "result_remittance_transaction_id")
    private Long resultRemittanceTransactionId;   // 체결 결과(REMITTANCE) — 타 도메인 ID

    @Column(name = "idempotency_key", length = 100, nullable = false, unique = true)
    private String idempotencyKey;                // 생성 멱등 키 (요청 재전송 방지)

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;                 // 실패 사유 — FAILED 일 때

    @Version
    private Long version;                          // 낙관적 락 — 이중 체결 방지 (수정될 때 +1)

    private Reservation(Long userId, ReservationAction action,
                        String fromCurrency, String toCurrency, BigDecimal amount,
                        BigDecimal targetRate, LocalDateTime expiresAt,
                        Long recipientId,
                        RemittanceReason remittanceReason, String remittanceReasonDetail,
                        String idempotencyKey) {
        // 잘못된 상태의 예약 생성을 막기 위한 필수값·불변식 검증
        if (userId == null
                || action == null
                || fromCurrency == null || fromCurrency.isBlank()
                || toCurrency == null || toCurrency.isBlank()
                || fromCurrency.equals(toCurrency)
                || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0
                || targetRate == null || targetRate.compareTo(BigDecimal.ZERO) <= 0
                || expiresAt == null
                || idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException(ReservationErrorCode.INVALID_RESERVATION_REQUEST);
        }
        // REMITTANCE 전용 검증: 수취인·송금 사유 필수
        if (action == ReservationAction.REMITTANCE) {
            if (recipientId == null || remittanceReason == null)
                throw new BusinessException(ReservationErrorCode.RECIPIENT_REQUIRED);
        }
        // EXCHANGE 전용 검증: 송금 전용 필드 혼입 방지
        if (action == ReservationAction.EXCHANGE) {
            if (recipientId != null || remittanceReason != null)
                throw new BusinessException(ReservationErrorCode.INVALID_RESERVATION_REQUEST);
        }

        this.userId = userId;
        this.action = action;
        this.fromCurrency = fromCurrency;
        this.toCurrency = toCurrency;
        this.amount = amount;
        this.targetRate = targetRate;
        this.type = ReservationType.TARGET_RATE;
        this.expiresAt = expiresAt;
        this.recipientId = recipientId;
        this.remittanceReason = remittanceReason;
        this.remittanceReasonDetail = remittanceReasonDetail;
        this.idempotencyKey = idempotencyKey;
        this.status = ReservationStatus.ACTIVE;   // 생성 시 항상 ACTIVE 강제
    }

    /** 예약 환전 생성 (월렛 내 환전 — 수취인·송금 사유 없음). */
    public static Reservation createExchange(Long userId,
            String fromCurrency, String toCurrency, BigDecimal amount,
            BigDecimal targetRate, LocalDateTime expiresAt, String idempotencyKey) {
        return new Reservation(userId, ReservationAction.EXCHANGE,
                fromCurrency, toCurrency, amount, targetRate, expiresAt,
                null, null, null, idempotencyKey);
    }

    /** 예약 송금 생성 (수취인 USD 수취 — recipientId·remittanceReason 필수). */
    public static Reservation createRemittance(Long userId,
            String fromCurrency, String toCurrency, BigDecimal amount,
            BigDecimal targetRate, LocalDateTime expiresAt,
            Long recipientId,
            RemittanceReason remittanceReason, String remittanceReasonDetail,
            String idempotencyKey) {
        return new Reservation(userId, ReservationAction.REMITTANCE,
                fromCurrency, toCurrency, amount, targetRate, expiresAt,
                recipientId, remittanceReason, remittanceReasonDetail, idempotencyKey);
    }

    // ── 상태 전이 (유효 전이만 허용, setter 비노출) ──

    /** 목표 도달 — 체결 시작. ACTIVE 에서만 가능 (이중 체결 방지). */
    public void markTriggered() {
        markTriggered(LocalDateTime.now());
    }

    /** 목표 도달 — 체결 시작. ACTIVE 에서만 가능 (이중 체결 방지). */
    public void markTriggered(LocalDateTime triggeredAt) {
        requireStatus(ReservationStatus.ACTIVE);
        if (triggeredAt == null) {
            throw new BusinessException(ReservationErrorCode.INVALID_RESERVATION_REQUEST);
        }
        this.triggeredAt = triggeredAt;
        this.status = ReservationStatus.TRIGGERED;
    }

    /** 환전 체결 완료 — TRIGGERED·EXCHANGE 에서만, 결과 환전 거래 ID 기록. */
    public void markCompletedAsExchange(Long exchangeTransactionId) {
        markCompletedAsExchange(exchangeTransactionId, LocalDateTime.now());
    }

    /** 환전 체결 완료 — TRIGGERED·EXCHANGE 에서만, 결과 환전 거래 ID 기록. */
    public void markCompletedAsExchange(Long exchangeTransactionId, LocalDateTime executedAt) {
        requireStatus(ReservationStatus.TRIGGERED);
        requireAction(ReservationAction.EXCHANGE);
        requireResult(exchangeTransactionId, executedAt);
        this.resultExchangeTransactionId = exchangeTransactionId;
        this.executedAt = executedAt;
        this.status = ReservationStatus.COMPLETED;
    }

    /** 송금 체결 완료 — TRIGGERED·REMITTANCE 에서만, 결과 송금 거래 ID 기록. */
    public void markCompletedAsRemittance(Long remittanceTransactionId) {
        markCompletedAsRemittance(remittanceTransactionId, LocalDateTime.now());
    }

    /** 송금 체결 완료 — TRIGGERED·REMITTANCE 에서만, 결과 송금 거래 ID 기록. */
    public void markCompletedAsRemittance(Long remittanceTransactionId, LocalDateTime executedAt) {
        requireStatus(ReservationStatus.TRIGGERED);
        requireAction(ReservationAction.REMITTANCE);
        requireResult(remittanceTransactionId, executedAt);
        this.resultRemittanceTransactionId = remittanceTransactionId;
        this.executedAt = executedAt;
        this.status = ReservationStatus.COMPLETED;
    }

    /** 체결 실패 (잔액 부족·한도 초과 등). 도달 후 실행 단계에서 호출. */
    public void fail(String reason) {
        requireStatus(ReservationStatus.TRIGGERED);
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ReservationErrorCode.INVALID_RESERVATION_REQUEST);
        }
        this.failureReason = reason;
        this.status = ReservationStatus.FAILED;
    }

    /** 사용자 취소 — 체결 전(ACTIVE)만 가능. */
    public void cancel() {
        requireStatus(ReservationStatus.ACTIVE);
        this.status = ReservationStatus.CANCELED;
    }

    /** 기한 만료 — 미도달(ACTIVE) 상태에서만. */
    public void expire() {
        requireStatus(ReservationStatus.ACTIVE);
        this.status = ReservationStatus.EXPIRED;
    }

    /** 현재 상태가 기대 상태가 아니면 전이 거부. */
    private void requireStatus(ReservationStatus expected) {
        if (this.status != expected) {
            throw new BusinessException(ReservationErrorCode.INVALID_STATUS_TRANSITION);
        }
    }

    /** 현재 동작이 기대 동작과 다르면 체결 거부 (교차 체결 방지). */
    private void requireAction(ReservationAction expected) {
        if (this.action != expected) {
            throw new BusinessException(ReservationErrorCode.RESERVATION_ACTION_MISMATCH);
        }
    }

    /** 결과 거래 ID와 체결 시각이 없으면 완료 상태로 전이하지 않는다. */
    private void requireResult(Long resultTransactionId, LocalDateTime executedAt) {
        if (resultTransactionId == null || executedAt == null) {
            throw new BusinessException(ReservationErrorCode.INVALID_RESERVATION_REQUEST);
        }
    }
}
