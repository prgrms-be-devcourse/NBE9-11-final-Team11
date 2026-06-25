package com.fxflow.domain.admin.service;

import com.fxflow.domain.admin.dto.AdminTransactionFilter;
import com.fxflow.domain.admin.dto.AdminTransactionItem;
import com.fxflow.domain.admin.dto.AdminTransactionPageResponse;
import com.fxflow.domain.admin.repository.AdminTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminTransactionService {

    private final AdminTransactionRepository adminTransactionRepository;

    @Transactional(readOnly = true)
    public AdminTransactionPageResponse getTransactions(AdminTransactionFilter filter, int page, int size) {
        List<AdminTransactionItem> data = adminTransactionRepository.findAll(filter, page, size);
        long totalElements = adminTransactionRepository.count(filter);
        return AdminTransactionPageResponse.of(data, page, size, totalElements);
    }
}