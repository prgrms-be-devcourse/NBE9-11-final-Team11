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

	// 비즈니스 로직은 KstClock.now()로 타임존을 명시하므로 이 블록에 의존하지 않지만,
	// Spring 컨텍스트 없이 동작하는 외부 라이브러리 등을 위한 보조 안전망으로 유지한다.
	static {
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
	}

	public static void main(String[] args) {
		SpringApplication.run(FxflowApplication.class, args);
	}

}
