package com.fxflow.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();

        // LocalDateTime 직렬화 설정
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.enable(SerializationFeature.WRITE_BIGDECIMAL_AS_PLAIN);
        // 직접 생성한 ObjectMapper 빈은 Spring Boot 자동 구성(spring.jackson.time-zone)이
        // 적용되지 않으므로 명시적으로 설정한다.
        objectMapper.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));

        return objectMapper;
    }
}
