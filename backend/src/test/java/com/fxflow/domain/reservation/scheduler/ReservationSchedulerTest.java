package com.fxflow.domain.reservation.scheduler;

import com.fxflow.domain.reservation.service.ReservationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReservationSchedulerTest {

    @Mock
    private ReservationService reservationService;

    @InjectMocks
    private ReservationScheduler reservationScheduler;

    @Test
    @DisplayName("만료 스케줄 - 만료 전이 서비스에 위임한다")
    void expireOverdueReservations_delegatesToService() {
        reservationScheduler.expireOverdueReservations();

        verify(reservationService).expireOverdueReservations(any());
    }
}
