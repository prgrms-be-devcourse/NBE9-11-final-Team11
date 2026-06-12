package com.fxflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@ConfigurationPropertiesScan
@SpringBootApplication
public class FxflowApplication {

	public static void main(String[] args) {
		SpringApplication.run(FxflowApplication.class, args);
	}

}
