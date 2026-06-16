package com.fxflow.domain.remittancetransaction.service;

import com.fxflow.domain.remittancetransaction.dto.response.RemittanceQuoteSnapshot;

/**
 * 송금 주문 생성에 필요한 견적 정보를 제공한다.
 * 현재는 Mock 구현체를 사용하며, 추후 Redis나 실제 견적 저장소 구현으로 교체할 수 있다.
 */
public interface RemittanceQuoteProvider {

    /**
     * quoteId로 송금 견적 정보를 조회한다.
     */
    RemittanceQuoteSnapshot getQuote(String quoteId);
}
