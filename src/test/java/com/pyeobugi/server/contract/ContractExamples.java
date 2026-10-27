package com.pyeobugi.server.contract;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.pyeobugi.server.common.time.KstTimeUtils;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** src/test/resources/contract/에 복사해 둔 AI 레포 docs/api/examples/*.json 읽기 */
final class ContractExamples {

	static final JsonMapper MAPPER = JsonMapper.builder().build();

	private ContractExamples() {
	}

	static String read(String file) throws IOException {
		try (InputStream in = ContractExamples.class.getResourceAsStream("/contract/" + file)) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	static JsonNode example(String file) throws IOException {
		return MAPPER.readTree(read(file));
	}

	/** 예시 리포트의 generatedAt(2026-10-25T21:35:00+09:00)과 같은 시각으로 고정한다 */
	@TestConfiguration(proxyBeanMethods = false)
	static class ExampleClock {

		@Bean
		@Primary
		Clock exampleClock() {
			return Clock.fixed(Instant.parse("2026-10-25T12:35:00Z"), KstTimeUtils.KST);
		}

	}

}
