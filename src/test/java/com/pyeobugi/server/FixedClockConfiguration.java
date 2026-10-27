package com.pyeobugi.server;

import java.time.Clock;
import java.time.Instant;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.pyeobugi.server.common.time.KstTimeUtils;

/** 서버 현재 시각을 2026-10-05T00:10:00+09:00으로 고정한다 (mock validate.test.ts의 NOW와 동일) */
@TestConfiguration(proxyBeanMethods = false)
public class FixedClockConfiguration {

	public static final Instant NOW = Instant.parse("2026-10-04T15:10:00Z");

	@Bean
	@Primary
	public Clock fixedClock() {
		return Clock.fixed(NOW, KstTimeUtils.KST);
	}

}
