package com.pyeobugi.server;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PyeobugiServerApplication {

	public static void main(String[] args) {
		// 서비스가 KST 전용이므로 JVM 기본 시간대를 고정 (plan 6-3)
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
		SpringApplication.run(PyeobugiServerApplication.class, args);
	}

}
