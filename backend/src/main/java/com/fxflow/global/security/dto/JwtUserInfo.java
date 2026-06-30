package com.fxflow.global.security.dto;

import java.util.Date;

public record JwtUserInfo(Long userId, String role, String jti, Date issuedAt) {
}