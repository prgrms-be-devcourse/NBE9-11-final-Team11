package com.fxflow.global.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

public record ErrorResponse(
        String code,
        String message,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        List<ValidationError> details
) {
    public static ErrorResponse from(ErrorCode errorCode) {
        return new ErrorResponse(
                errorCode.getCode(),
                errorCode.getMessage(),
                null
        );
    }

    public static ErrorResponse of(String code, String message, List<ValidationError> details) {
        return new ErrorResponse(code, message, details);
    }
}
