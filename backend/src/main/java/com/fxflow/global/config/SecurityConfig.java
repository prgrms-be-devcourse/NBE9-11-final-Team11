package com.fxflow.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fxflow.global.exception.ErrorResponse;
import com.fxflow.global.security.JwtAuthenticationFilter;
import com.fxflow.global.security.JwtTokenProvider;
import com.fxflow.global.security.TokenBlacklistService;
import com.fxflow.global.security.errorcode.AuthErrorCode;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Slf4j
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;
    private final TokenBlacklistService tokenBlacklistService;
    private final ObjectMapper objectMapper;
    private final CorsConfigurationSource corsConfigurationSource;

    // dev security backdoor 관련 코드
    private Filter testUserFilter;
    @Autowired(required = false)
    @Qualifier("testUserFilter")
    public void setTestUserFilter(Filter testUserFilter) {
        this.testUserFilter = testUserFilter;
    }
    // ----------------------

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        if (testUserFilter != null) {
            http.addFilterBefore(testUserFilter, UsernamePasswordAuthenticationFilter.class);
        }

        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, e) -> {
                            log.warn("[Security] 인증 실패 — uri={}", request.getRequestURI());
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write(
                                    objectMapper.writeValueAsString(
                                            ErrorResponse.from(AuthErrorCode.UNAUTHORIZED)
                                    )
                            );
                        })
                        .accessDeniedHandler((request, response, e) -> {
                            log.warn("[Security] 권한 없음 — uri={}", request.getRequestURI());
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write(
                                    objectMapper.writeValueAsString(
                                            ErrorResponse.from(AuthErrorCode.ACCESS_DENIED)
                                    )
                            );
                        })
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/v1/auth/login",
                                "/api/v1/auth/signup",
                                "/api/v1/auth/refresh",
                                "/api/v1/mockbank/check",
                                "/api/v1/mockbank/inquiry/usd"
                        ).permitAll()
                        .requestMatchers("/api/v1/fxrates/**").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/swagger-resources/**",
                                "/webjars/**"
                        ).permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtTokenProvider, tokenBlacklistService),
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authenticationConfiguration
    ) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }
}