package com.fxflow.global.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Arrays;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CookieTokenExtractor {

    private static final String ACCESS_COOKIE_NAME = "accessToken";
    private static final String REFRESH_COOKIE_NAME = "refreshToken";

    // Access Token 추출
    public static String extract(HttpServletRequest request) {
        return extractByCookieName(request, ACCESS_COOKIE_NAME);
    }

    // Refresh Token 추출
    public static String extractRefresh(HttpServletRequest request) {
        return extractByCookieName(request, REFRESH_COOKIE_NAME);
    }

    private static String extractByCookieName(HttpServletRequest request, String cookieName) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
                .filter(cookie -> cookieName.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}