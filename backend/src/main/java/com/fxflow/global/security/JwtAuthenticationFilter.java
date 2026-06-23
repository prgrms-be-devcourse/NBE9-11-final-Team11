package com.fxflow.global.security;

import com.fxflow.global.exception.BusinessException;
import com.fxflow.global.security.dto.JwtUserInfo;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String token = CookieTokenExtractor.extract(request);
        if (token != null) {
            try {
                JwtUserInfo jwtUserInfo = jwtTokenProvider.getJwtUserInfo(token);

                log.debug("[JWT] 인증 성공 — userId={}, role={}",
                        jwtUserInfo.userId(), jwtUserInfo.role());

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                jwtUserInfo.userId(),
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + jwtUserInfo.role()))
                        );
                SecurityContextHolder.getContext().setAuthentication(authentication);

            } catch (BusinessException e) {
                log.warn("[JWT] 인증 실패 — uri={}", request.getRequestURI());
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}