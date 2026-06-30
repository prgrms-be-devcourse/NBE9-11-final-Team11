package com.fxflow.domain.remittancetransaction.errorcode;

import com.fxflow.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum RecipientErrorCode implements ErrorCode {

    // 수취인 등록
    DUPLICATE_RECIPIENT(HttpStatus.CONFLICT, "DUPLICATE_RECIPIENT", "계좌번호를 다시 확인해주세요."),
    INVALID_ACCOUNT_NUMBER(HttpStatus.BAD_REQUEST, "INVALID_RECIPIENT_ACCOUNT_NUMBER", "계좌번호를 다시 확인해주세요."),

    // 수취인 조회
    RECIPIENT_NOT_FOUND(HttpStatus.NOT_FOUND, "REMITTANCE_RECIPIENT_NOT_FOUND", "수취인을 찾을 수 없습니다."),

    ;


    private final HttpStatus status;
    private final String code;
    private final String message;
}
