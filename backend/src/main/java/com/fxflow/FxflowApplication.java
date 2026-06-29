package com.fxflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@EnableAsync
@EnableScheduling
@ConfigurationPropertiesScan
@SpringBootApplication
public class FxflowApplication {

	// JVM 기본 타임존이 실행 환경(운영 docker, 로컬, CI)마다 달라
	// LocalDateTime.now() 기준 시각이 어긋나는 것을 막기 위해 클래스 로드 시점에 고정한다.
	static {
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
	}

	public static void main(String[] args) {
		SpringApplication.run(FxflowApplication.class, args);
	}

}
