package com.fxflow.global.exception;

public record ValidationError(
        String field,
        String reason
) {
}