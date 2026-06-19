package com.fxflow.domain.user.dto.response;

import com.fxflow.domain.user.entity.User;

public record UserCheckResponse(
        String email,
        String name
) {
    public static UserCheckResponse of(User user) {
        return new UserCheckResponse(user.getEmail(), user.getName());
    }
}
