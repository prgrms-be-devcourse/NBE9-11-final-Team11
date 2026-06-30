package com.fxflow.domain.user.dto.response;

import com.fxflow.domain.user.entity.User;

import java.time.LocalDateTime;

public record SignupResponse(
        Long userId,
        String email,
        String name,
        Boolean mockAccountLinked,
        LocalDateTime createdAt
) {
    public static SignupResponse of (User user){
        return new SignupResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                false,  // 가입 직후 모의계좌 미연결
                user.getCreatedAt()
        );
    }
}
