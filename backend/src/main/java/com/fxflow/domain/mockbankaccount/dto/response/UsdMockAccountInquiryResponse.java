package com.fxflow.domain.mockbankaccount.dto.response;

import com.fxflow.domain.wallet.dto.response.TransactionHistoryResponse;
import java.math.BigDecimal;

public record UsdMockAccountInquiryResponse(
        BigDecimal balance,
        String currencyCode,
        TransactionHistoryResponse transactionHistory
) {}