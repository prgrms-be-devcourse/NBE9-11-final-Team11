package com.fxflow.domain.remittancetransaction.service;

import com.fxflow.domain.remittancetransaction.dto.response.RemittanceQuoteSnapshot;
import com.fxflow.domain.remittancetransaction.errorcode.RemittanceTransactionErrorCode;
import com.fxflow.global.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class MockRemittanceQuoteProvider implements RemittanceQuoteProvider {

    private static final String MOCK_QUOTE_ID = "TQUOTE-001";

    /**
     * 임시 quoteId를 검증하고 Mock 송금 견적 정보를 반환한다.
     */
    @Override
    public RemittanceQuoteSnapshot getQuote(String quoteId) {
        if (!MOCK_QUOTE_ID.equals(quoteId)) {
            throw new BusinessException(RemittanceTransactionErrorCode.QUOTE_NOT_FOUND);
        }

        return new RemittanceQuoteSnapshot(
                MOCK_QUOTE_ID,
                1L,
                "KRW",
                new BigDecimal("1000000.00"),
                "USD",
                new BigDecimal("736.52"),
                new BigDecimal("1351.00000000"),
                new BigDecimal("8000.00"),
                new BigDecimal("1000000.00"),
                new BigDecimal("736.52")
        );
    }
}
