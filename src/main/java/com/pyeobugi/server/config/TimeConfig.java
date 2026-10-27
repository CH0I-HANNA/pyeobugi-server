package com.pyeobugi.server.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.pyeobugi.server.common.time.KstTimeUtils;

@Configuration(proxyBeanMethods = false)
public class TimeConfig {

	/** 테스트에서는 Clock.fixed(...)로 바꿔 시각을 고정한다 */
	@Bean
	public Clock clock() {
		return Clock.system(KstTimeUtils.KST);
	}

}
