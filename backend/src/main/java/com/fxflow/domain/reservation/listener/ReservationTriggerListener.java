package com.fxflow.domain.reservation.listener;

import com.fxflow.domain.reservation.entity.Reservation;
import com.fxflow.domain.reservation.enums.ReservationAction;
import com.fxflow.domain.reservation.enums.ReservationStatus;
import com.fxflow.domain.reservation.repository.ReservationRepository;
import com.fxflow.domain.reservation.service.ReservationExecutionService;
import com.fxflow.global.fx.FxRateSnapshot;
import com.fxflow.global.fx.FxRateUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 환율 갱신 시 목표가에 도달한 ACTIVE 예약 환전을 체결 (RSV-02).
 * 환율 저장 트랜잭션 커밋 이후(AFTER_COMMIT) 동작하며, 한 건의 실패가 다른 건을 막지 않도록 예약별로 독립 처리
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationTriggerListener {

    private static final String KRW = "KRW";
    private static final String USD = "USD";

    private final ReservationRepository reservationRepository;
    private final ReservationExecutionService reservationExecutionService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFxRateUpdated(FxRateUpdatedEvent event) {
        try {
            FxRateSnapshot snapshot = event.snapshot();
            // 현재는 USD/KRW 예약 환전만 지원
            if (!USD.equals(snapshot.baseCurrency()) || !KRW.equals(snapshot.quoteCurrency())) {
                return;
            }

            List<Reservation> candidates =
                    reservationRepository.findByStatusAndAction(ReservationStatus.ACTIVE, ReservationAction.EXCHANGE);

            for (Reservation reservation : candidates) {
                if (!isTargetReached(reservation, snapshot)) {
                    continue;
                }
                try {
                    // 선점에 성공한 경우에만 체결(이중 체결 방지). 선점 실패·예외는 다음 환율 이벤트에서 재시도
                    if (reservationExecutionService.preempt(reservation.getId())) {
                        reservationExecutionService.executeTriggered(reservation.getId());
                    }
                } catch (Exception e) {
                    log.warn("예약 환전 체결 처리 중 오류. reservationId={}", reservation.getId(), e);
                }
            }
        } catch (Exception e) {
            // 후보 조회 등 리스너 전체가 실패해도 이벤트 처리만 중단(환율 수집 트랜잭션엔 영향 없음)
            log.error("예약 체결 트리거 처리 실패", e);
        }
    }

    /**
     * 목표 환율 도달 판정 — 체결 적용가(매수/매도) 기준이라 실제 체결 환율이 목표를 반드시 만족하도록.
     * 매수(KRW→USD): buyRate ≤ target / 매도(USD→KRW): sellRate ≥ target
     * 만료 시각이 지난 예약은 제외(만료 전이는 별도 스케줄러가 담당). 만료 시각이 null 이면 무기한이라 통과
     */
    private boolean isTargetReached(Reservation reservation, FxRateSnapshot snapshot) {
        // null = 무기한(만료 없음) → 통과. 값이 있고 이미 지난 예약만 제외.
        if (reservation.getExpiresAt() != null && !reservation.getExpiresAt().isAfter(LocalDateTime.now())) {
            return false;
        }
        String from = reservation.getFromCurrency();
        String to = reservation.getToCurrency();
        BigDecimal target = reservation.getTargetRate();
        if (KRW.equals(from) && USD.equals(to)) {
            return snapshot.buyRate().compareTo(target) <= 0;
        }
        if (USD.equals(from) && KRW.equals(to)) {
            return snapshot.sellRate().compareTo(target) >= 0;
        }
        return false;
    }
}
