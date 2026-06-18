package com.fxflow.domain.remittancetransaction.service;

import com.fxflow.domain.remittancetransaction.dto.cache.RemittanceQuoteCache;
import com.fxflow.domain.remittancetransaction.dto.response.RemittanceQuoteSnapshot;
import com.fxflow.domain.remittancetransaction.errorcode.RemittanceTransactionErrorCode;
import com.fxflow.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RedisRemittanceQuoteProvider implements RemittanceQuoteProvider {

    private static final String REMITTANCE_QUOTE_KEY_PREFIX = "remittance:quote:";

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * quoteId로 Redis에 저장된 해외송금 견적 정보를 조회한다.
     */
    @Override
    public RemittanceQuoteSnapshot getQuote(String quoteId) {
        Object value = redisTemplate.opsForValue().get(createQuoteKey(quoteId));

        if (!(value instanceof RemittanceQuoteCache cache)) {
            throw new BusinessException(RemittanceTransactionErrorCode.QUOTE_NOT_FOUND);
        }

        return RemittanceQuoteSnapshot.from(quoteId, cache);
    }

    /**
     * 해외송금 견적 Redis key를 생성한다.
     */
    private String createQuoteKey(String quoteId) {
        return REMITTANCE_QUOTE_KEY_PREFIX + quoteId;
    }
}