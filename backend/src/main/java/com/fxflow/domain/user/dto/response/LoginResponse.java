package com.fxflow.domain.user.dto.response;

import com.fxflow.domain.user.entity.User;

public record LoginResponse(
        Long userId,
        String name,
        String email,
        boolean verified
) {
    public static LoginResponse of(User user){
        return new LoginResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                "COMPLETED".equals(user.getKycStatus())
        );
    }
}
