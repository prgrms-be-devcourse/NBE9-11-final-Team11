package com.fxflow.domain.admin.controller;

import com.fxflow.domain.admin.dto.AdminTransactionFilter;
import com.fxflow.domain.admin.dto.AdminTransactionPageResponse;
import com.fxflow.domain.admin.service.AdminTransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/transactions")
public class AdminTransactionController {

    private final AdminTransactionService adminTransactionService;

    @GetMapping
    public ResponseEntity<AdminTransactionPageResponse> getTransactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to
    ) {
        AdminTransactionFilter filter = new AdminTransactionFilter(from, to);
        return ResponseEntity.ok(adminTransactionService.getTransactions(filter, page, size));
    }
}
