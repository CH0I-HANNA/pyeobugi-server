package com.pyeobugi.server.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletResponse;

import tools.jackson.databind.json.JsonMapper;

class ErrorResponseWriterTest {

	private final ErrorResponseWriter writer = new ErrorResponseWriter(JsonMapper.builder().build());

	@AfterEach
	void clearMdc() {
		MDC.clear();
	}

	@Test
	void writesErrorCodeAsJsonWithTraceId() throws Exception {
		MDC.put(ErrorResponse.TRACE_ID_MDC_KEY, "req-7f3a9c2e");
		MockHttpServletResponse response = new MockHttpServletResponse();

		writer.write(response, ErrorCode.DEVICE_ID_INVALID);

		assertThat(response.getStatus()).isEqualTo(400);
		assertThat(response.getContentType()).startsWith("application/json");
		assertThat(response.getContentAsString())
				.contains("\"code\":\"DEVICE_ID_INVALID\"")
				.contains("\"traceId\":\"req-7f3a9c2e\"")
				.doesNotContain("retryAfter");
	}

	@Test
	void rateLimitedIncludesRetryAfter() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();

		writer.write(response, 429, ErrorResponse.rateLimited(30));

		assertThat(response.getStatus()).isEqualTo(429);
		assertThat(response.getContentAsString())
				.contains("\"code\":\"RATE_LIMITED\"")
				.contains("\"retryAfter\":30")
				.doesNotContain("traceId");
	}

}
