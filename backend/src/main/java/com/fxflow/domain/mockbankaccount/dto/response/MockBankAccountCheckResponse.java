package com.fxflow.domain.mockbankaccount.dto.response;

public record MockBankAccountCheckResponse(
        boolean available,
        String message
) {
    public static MockBankAccountCheckResponse success() {
        return new MockBankAccountCheckResponse(true, "사용 가능한 계좌번호입니다.");
    }

    public static MockBankAccountCheckResponse unavailable(String message) {
        return new MockBankAccountCheckResponse(false, message);
    }
}