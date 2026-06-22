// backend/src/main/java/com/fxflow/domain/mockbankaccount/dto/response/UsdMockAccountInquiryResponse.java
package com.fxflow.domain.mockbankaccount.dto.response;

import com.fxflow.global.util.CurrencyAmountFormatter;
import org.springframework.data.domain.Page;
import java.math.BigDecimal;

public record UsdMockAccountInquiryResponse(
        BigDecimal balance,
        String currencyCode,
        PagedReceiptResponse remittanceReceipts
) {
    public static UsdMockAccountInquiryResponse of(
            BigDecimal balance, String currencyCode, Page<RemittanceReceiptDto> receipts
    ) {
        return new UsdMockAccountInquiryResponse(
                CurrencyAmountFormatter.format(balance, currencyCode),
                currencyCode,
                PagedReceiptResponse.from(receipts)
        );
    }
}