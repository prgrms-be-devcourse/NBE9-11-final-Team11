package com.fxflow.domain.remittancetransaction.validator;

import com.fxflow.domain.userlimitusage.entity.UserLimitUsage;
import com.fxflow.domain.userlimitusage.repository.UserLimitUsageRepository;
import com.fxflow.global.exception.BusinessException;
import com.fxflow.global.exception.GlobalErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
public class RemittanceValidator {

    private final UserLimitUsageRepository userLimitUsageRepository;

    // 외국환거래법 규정 한도 상수
    private static final BigDecimal MAX_PER_TRANSACTION_USD = new BigDecimal("5000.00");
    private static final BigDecimal MAX_PER_YEAR_USD = new BigDecimal("100000.00");

    /**
     * 해외송금 한도 검증 로직
     * 1. 건당 한도 ($5,000) 초과 여부 확인
     * 2. 연간 누적 한도 ($100,000) 초과 여부 확인 (DB 실제 조회)
     */
    public void validateLimits(Long userId, BigDecimal requestAmountUsd) {
        log.info("[한도검증] 유저 ID: {}의 송금 한도 검증 시작. 요청금액: {} USD", userId, requestAmountUsd);

        // 1. 건당 한도 체크
        if (requestAmountUsd.compareTo(MAX_PER_TRANSACTION_USD) > 0) {
            log.warn("[한도검증 실패] 건당 한도 초과 - 유저: {}, 요청: {}", userId, requestAmountUsd);
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }

        // 2. 연간 누적액 실제 DB 조회
        // 해당 유저의 기록이 없으면(첫 송금 등) 누적액은 0으로 처리
        BigDecimal currentYearTotalUsd = userLimitUsageRepository.findById(userId)
                .map(UserLimitUsage::getAnnualUsedUsd)
                .orElse(BigDecimal.ZERO);

        // 3. 연간 누적 한도 체크
        BigDecimal expectedYearTotal = currentYearTotalUsd.add(requestAmountUsd);
        if (expectedYearTotal.compareTo(MAX_PER_YEAR_USD) > 0) {
            log.warn("[한도검증 실패] 연간 누적 한도 초과 - 유저: {}, 기존 누적: {}, 요청: {}",
                    userId, currentYearTotalUsd, requestAmountUsd);
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }

        log.info("[한도검증 성공] 유저 ID: {} 송금 가능", userId);
    }
}