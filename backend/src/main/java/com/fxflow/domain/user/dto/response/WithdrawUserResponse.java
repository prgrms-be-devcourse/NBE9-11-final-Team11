package com.fxflow.domain.user.dto.response;

import com.fxflow.domain.user.entity.User;

import java.time.LocalDateTime;

public record WithdrawUserResponse(
        Long userId,
        String status,
        LocalDateTime deactivatedAt
) {
    public static WithdrawUserResponse of (User user, LocalDateTime withdrawnDate){
        return new WithdrawUserResponse(
                user.getId(),
                user.getStatus().name(),
                withdrawnDate
        );
    }
}
