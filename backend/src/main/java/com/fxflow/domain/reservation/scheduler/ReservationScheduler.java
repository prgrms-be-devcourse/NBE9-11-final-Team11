package com.fxflow.domain.reservation.scheduler;

import com.fxflow.domain.reservation.service.ReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 기한이 지난 예약을 주기적으로 EXPIRED 로 전이한다
 * 예약마다 스케줄을 만들지 않고, 일정 주기마다 만료 대상을 한 번에 찾는다(송금 만료 스케줄러와 동일 방식).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationScheduler {

    private final ReservationService reservationService;

    @Scheduled(fixedDelayString = "${reservation.expiration-scheduler.fixed-delay-ms:60000}")
    public void expireOverdueReservations() {
        int expiredCount = reservationService.expireOverdueReservations(LocalDateTime.now());

        if (expiredCount > 0) {
            log.info("기한 만료 예약 자동 전이 완료. count={}", expiredCount);
        }
    }
}
