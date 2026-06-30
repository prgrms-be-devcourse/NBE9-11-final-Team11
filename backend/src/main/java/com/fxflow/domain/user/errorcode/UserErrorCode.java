// domain/user/errorcode/UserErrorCode.java
package com.fxflow.domain.user.errorcode;

import com.fxflow.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다."),
    EMAIL_DUPLICATED(HttpStatus.CONFLICT, "EMAIL_DUPLICATED", "이미 사용 중인 이메일입니다."),
    PASSWORD_POLICY_VIOLATION(HttpStatus.BAD_REQUEST, "PASSWORD_POLICY_VIOLATION", "비밀번호 정책을 위반했습니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "이메일 또는 비밀번호가 일치하지 않습니다."),
    WITHDRAWAL_BLOCKED(HttpStatus.CONFLICT, "WITHDRAWAL_BLOCKED", "잔액 또는 진행 중 거래가 있어 탈퇴할 수 없습니다."),
    ALREADY_WITHDRAWN(HttpStatus.CONFLICT, "ALREADY_WITHDRAWN", "이미 탈퇴한 회원입니다."),
    PASSWORD_MISMATCH(HttpStatus.UNAUTHORIZED, "PASSWORD_MISMATCH", "현재 비밀번호가 일치하지 않습니다."),
    SAME_AS_OLD_PASSWORD(HttpStatus.BAD_REQUEST, "SAME_AS_OLD_PASSWORD", "기존 비밀번호와 동일한 비밀번호로는 변경할 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}