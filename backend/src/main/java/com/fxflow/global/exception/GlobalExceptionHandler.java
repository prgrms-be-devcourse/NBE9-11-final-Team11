package com.fxflow.global.exception;


import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        log.error("BusinessException: {}", e.getMessage());
        ErrorCode code = e.getErrorCode();
        return ResponseEntity
                .status(code.getStatus())
                .body(ErrorResponse.from(code));

    }

    // @Valid 유효성 검사 실패 시 처리. @RequestBody 검증
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        log.error("MethodArgumentNotValidException 발생");
        List<ValidationError> errors = e.getBindingResult().getFieldErrors().stream()
                .map(err -> new ValidationError(err.getField(), err.getDefaultMessage()))
                .toList();

        return ResponseEntity
                .badRequest()
                .body(ErrorResponse.of("INVALID_INPUT", "입력값이 올바르지 않습니다.", errors));
    }

    // @Validated 유효성 검사 실패 처리. @RequestParam, @PathVariable 검증
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(ConstraintViolationException e) {
        log.error("ConstraintViolationException 발생");
        List<ValidationError> errors = e.getConstraintViolations().stream()
                .map(v -> new ValidationError(extractField(v.getPropertyPath()), v.getMessage()))
                .toList();

        ErrorCode errorCode = GlobalErrorCode.INVALID_INPUT_VALUE; // 공통 에러 코드 사용
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ErrorResponse.of(errorCode.getCode(), errorCode.getMessage(), errors));
    }

    // ConstraintViolationException에서 필드명만 추출하는 헬퍼 메서드
    private String extractField(Path path) {
        String field = null;
        for (Path.Node node : path) {
            field = node.getName();
        }
        return field;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("Unhandled Exception: ", e);
        ErrorCode errorCode = GlobalErrorCode.INTERNAL_SERVER_ERROR;
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ErrorResponse.from(errorCode));
    }
}
