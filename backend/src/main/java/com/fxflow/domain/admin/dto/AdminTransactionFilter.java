package com.fxflow.domain.admin.dto;

import java.time.LocalDate;

public record AdminTransactionFilter(
        LocalDate from,
        LocalDate to
) {
}
