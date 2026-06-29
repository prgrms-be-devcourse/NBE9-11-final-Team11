package com.fxflow.domain.mockbankaccount.exception;

import com.fxflow.domain.mockbankaccount.errorcode.MockBankAccountErrorCode;
import com.fxflow.global.exception.BusinessException;

/**
 * 1원 인증 코드 불일치 전용 예외.
 * verifyKyc()의 @Transactional(noRollbackFor = KycCodeMismatchException.class) 대상으로 사용되어,
 * 시도 횟수 증가분은 커밋되고 나머지 변경(없음)만 의미상 "실패"로 처리된다.
 * 다른 BusinessException(만료, 시도초과 등)은 이 예외가 아니므로 정상적으로 롤백된다.
 */
public class KycCodeMismatchException extends BusinessException {

    public KycCodeMismatchException(int remainingAttempts) {
        super(
                MockBankAccountErrorCode.KYC_CODE_MISMATCH,
                "인증코드가 일치하지 않습니다. (남은 시도 %d회)".formatted(remainingAttempts)
        );
    }
}
