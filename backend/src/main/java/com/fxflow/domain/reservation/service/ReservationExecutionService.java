package com.fxflow.domain.reservation.service;

import com.fxflow.domain.reservation.entity.Reservation;
import com.fxflow.domain.reservation.enums.ReservationStatus;
import com.fxflow.domain.reservation.repository.ReservationRepository;
import com.fxflow.global.exception.BusinessException;
import com.fxflow.global.exchange.ExchangeExecutionCommand;
import com.fxflow.global.exchange.ExchangeExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 목표가 도달 예약의 체결 실행 (RSV-02 예약 환전)
 *
 * 2단계로 나누는 이유:
 * 1) 선점({@link #preempt}) — ACTIVE→TRIGGERED 를 먼저 커밋해, 동시/다중 인스턴스의 이중 체결을 {@code @Version} 낙관락으로 차단(충돌한 쪽은 false 로 건너뜀).
 * 2) 체결({@link #executeTriggered}) — 환전 실행({@link ExchangeExecutor})은 별도 트랜잭션이라 실패해도 이 트랜잭션이 오염되지 않아 FAILED 로 기록 가능.
 *
 * 두 메서드 모두 REQUIRES_NEW — AFTER_COMMIT 이벤트 리스너에서 호출되어 활성 트랜잭션이 없으므로 각자 새 트랜잭션으로 커밋해야 상태 전이가 DB에 반영됨.
 *
 * 체결 시 트랜잭션은 둘로 나뉜다(의도)
 * - executeTriggered 트랜잭션 — 예약 상태(COMPLETED/FAILED) 기록용.
 * - 환전 실행({@link ExchangeExecutor})의 REQUIRES_NEW 트랜잭션 — 실제 자금 이동용.
 * 환전이 실패해 환전 트랜잭션이 롤백돼도 executeTriggered 트랜잭션은 오염되지 않아 FAILED 를 정상 커밋할 수 있다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationExecutionService {

    private final ReservationRepository reservationRepository;
    private final ExchangeExecutor exchangeExecutor;

    /**
     * 체결 선점 — ACTIVE 예약을 TRIGGERED 로 전이.
     * 이미 취소·체결·만료되었으면 false 반환.
     * 동시·다중 인스턴스 선점은 REQUIRES_NEW 커밋 시점에 @Version 충돌이 발생하며,
     * 그 예외는 호출자(리스너)의 try-catch 에서 처리되어 해당 예약만 건너뛴다(다음 주기 재시도).
     *
     * 리스너가 넘긴 ID 로 이 새 트랜잭션에서 다시 조회하는 이유 —
     * 리스너의 후보 엔티티는 영속성 컨텍스트 밖이라, 여기서 재조회해야 변경 감지와 @Version 락이 적용됨
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean preempt(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId).orElse(null);
        if (reservation == null || reservation.getStatus() != ReservationStatus.ACTIVE) {
            return false;
        }
        reservation.markTriggered();
        return true;
    }

    /**
     * 선점된(TRIGGERED) 예약 환전을 체결.
     * 성공 시 COMPLETED·결과 거래 ID 기록, 비즈니스 실패(잔액 부족·한도 초과 등) 시 FAILED 로 기록.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void executeTriggered(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId).orElse(null);
        if (reservation == null || reservation.getStatus() != ReservationStatus.TRIGGERED) {
            return;
        }
        try {
            Long exchangeTransactionId = exchangeExecutor.execute(new ExchangeExecutionCommand(
                    reservation.getUserId(),
                    reservation.getFromCurrency(),
                    reservation.getToCurrency(),
                    reservation.getAmount()));
            reservation.markCompletedAsExchange(exchangeTransactionId);
            log.info("예약 환전 체결 완료. reservationId={}", reservationId);
        } catch (BusinessException e) {
            // 롤백된 것은 환전 실행(WalletExchangeExecutor)의 REQUIRES_NEW 트랜잭션뿐 — 자금 이동은 없다.
            // 이 트랜잭션(executeTriggered)은 정상이므로 FAILED 사유를 그대로 커밋한다.
            reservation.fail(e.getErrorCode().getMessage());
            log.info("예약 환전 체결 실패. reservationId={}, code={}", reservationId, e.getErrorCode().getCode());
        }
    }
}
