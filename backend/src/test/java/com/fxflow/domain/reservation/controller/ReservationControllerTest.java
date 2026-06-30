package com.fxflow.domain.reservation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fxflow.domain.reservation.dto.request.ReservationCreateRequest;
import com.fxflow.domain.reservation.dto.response.ReservationPageResponse;
import com.fxflow.domain.reservation.dto.response.ReservationResponse;
import com.fxflow.domain.reservation.enums.ReservationAction;
import com.fxflow.domain.reservation.enums.ReservationStatus;
import com.fxflow.domain.reservation.exception.ReservationErrorCode;
import com.fxflow.domain.reservation.service.ReservationService;
import com.fxflow.global.exception.BusinessException;
import com.fxflow.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReservationControllerTest {

    private static final Long USER_ID = 1L;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private ReservationService reservationService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        reservationService = mock(ReservationService.class);

        mockMvc = MockMvcBuilders.standaloneSetup(new ReservationController(reservationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(USER_ID, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private ReservationResponse sampleResponse(ReservationStatus status) {
        return new ReservationResponse(
                10L, ReservationAction.EXCHANGE, status,
                "KRW", "USD", new BigDecimal("1000000"), new BigDecimal("1300.00000000"),
                LocalDateTime.now().plusDays(7),
                null, null, null, null, null, null, null, null,
                LocalDateTime.now());
    }

    @Test
    @DisplayName("POST /api/v1/reservations — 생성 성공 시 201")
    void create_returns201() throws Exception {
        ReservationCreateRequest request = new ReservationCreateRequest(
                ReservationAction.EXCHANGE, "KRW", "USD",
                new BigDecimal("1000000"), new BigDecimal("1300"),
                LocalDateTime.now().plusDays(7), null, null, null);
        when(reservationService.create(eq(USER_ID), any(), eq("key-1")))
                .thenReturn(sampleResponse(ReservationStatus.ACTIVE));

        mockMvc.perform(post("/api/v1/reservations")
                        .header("Idempotency-Key", "key-1")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reservationId").value(10))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("POST /api/v1/reservations — Idempotency-Key 헤더 누락이면 400")
    void create_missingIdempotencyKey_returns400() throws Exception {
        ReservationCreateRequest request = new ReservationCreateRequest(
                ReservationAction.EXCHANGE, "KRW", "USD",
                new BigDecimal("1000000"), new BigDecimal("1300"),
                LocalDateTime.now().plusDays(7), null, null, null);

        mockMvc.perform(post("/api/v1/reservations")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/reservations — 필수값 누락이면 400")
    void create_invalidRequest_returns400() throws Exception {
        ReservationCreateRequest invalid = new ReservationCreateRequest(
                null, "KRW", "USD",
                new BigDecimal("1000000"), new BigDecimal("1300"),
                LocalDateTime.now().plusDays(7), null, null, null);

        mockMvc.perform(post("/api/v1/reservations")
                        .header("Idempotency-Key", "key-1")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/reservations — 목록 조회 200")
    void getReservations_returns200() throws Exception {
        ReservationPageResponse page = new ReservationPageResponse(
                List.of(sampleResponse(ReservationStatus.ACTIVE)), 0, 20, 1, 1);
        when(reservationService.getReservations(USER_ID, 0, 20)).thenReturn(page);

        mockMvc.perform(get("/api/v1/reservations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/reservations/{id} — 본인 예약이 없으면 404")
    void getReservation_notFound_returns404() throws Exception {
        when(reservationService.getReservation(USER_ID, 99L))
                .thenThrow(new BusinessException(ReservationErrorCode.RESERVATION_NOT_FOUND));

        mockMvc.perform(get("/api/v1/reservations/{id}", 99L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("R-005"));
    }

    @Test
    @DisplayName("PATCH /api/v1/reservations/{id}/cancel — 취소 성공 200")
    void cancel_returns200() throws Exception {
        when(reservationService.cancel(USER_ID, 10L))
                .thenReturn(sampleResponse(ReservationStatus.CANCELED));

        mockMvc.perform(patch("/api/v1/reservations/{id}/cancel", 10L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"));
    }
}
