package com.pyeobugi.server.common.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.pyeobugi.server.common.error.ErrorResponseWriter;
import com.pyeobugi.server.common.security.IpHasher;
import com.pyeobugi.server.config.RateLimitProperties;
import com.pyeobugi.server.config.RateLimitProperties.Limit;

import io.github.bucket4j.ConsumptionProbe;
import tools.jackson.databind.json.JsonMapper;

class RateLimitFilterTest {

	private static final String DEVICE_A = "3f2b8c1e-6a4d-4e0b-9b7a-0c1d2e3f4a5b";
	private static final String DEVICE_B = "aaaaaaaa-0000-4000-8000-000000000000";

	/** 기기 2회, IP 3회. 분당 1개씩만 채워지므로 테스트 도중 다시 채워지지 않는다 */
	private final RateLimitFilter filter = new RateLimitFilter(
			new RateLimitProperties(new Limit(2, 1), new Limit(3, 1), Duration.ofMinutes(10)),
			new IpHasher("test-secret-0123456789abcdefghijklmn"),
			new ErrorResponseWriter(JsonMapper.builder().build()));

	@Test
	void deviceLimitReturns429WithRetryAfterInBodyAndHeader() throws Exception {
		assertPassed(send(DEVICE_A, "10.0.0.1"));
		assertPassed(send(DEVICE_A, "10.0.0.2"));

		MockHttpServletResponse limited = send(DEVICE_A, "10.0.0.3");

		assertThat(limited.getStatus()).isEqualTo(429);
		assertThat(limited.getContentAsString()).contains("\"code\":\"RATE_LIMITED\"").containsPattern("\"retryAfter\":\\d+");
		int retryAfter = Integer.parseInt(limited.getHeader("Retry-After"));
		assertThat(retryAfter).isBetween(1, 60);
		assertThat(limited.getContentAsString()).contains("\"retryAfter\":" + retryAfter);
		// 다른 기기는 영향 없음
		assertPassed(send(DEVICE_B, "10.0.0.4"));
	}

	@Test
	void ipLimitAppliesAcrossDevices() throws Exception {
		assertPassed(send(DEVICE_A, "10.0.0.9"));
		assertPassed(send(DEVICE_B, "10.0.0.9"));
		assertPassed(send("bbbbbbbb-0000-4000-8000-000000000000", "10.0.0.9"));

		assertThat(send("cccccccc-0000-4000-8000-000000000000", "10.0.0.9").getStatus()).isEqualTo(429);
		assertPassed(send("cccccccc-0000-4000-8000-000000000000", "10.0.0.10"));
	}

	@Test
	void nonApiAndPreflightAreNotLimited() throws Exception {
		for (int i = 0; i < 5; i++) {
			MockFilterChain chain = new MockFilterChain();
			filter.doFilter(new MockHttpServletRequest("GET", "/swagger-ui/index.html"), new MockHttpServletResponse(), chain);
			assertThat(chain.getRequest()).isNotNull();

			MockFilterChain preflight = new MockFilterChain();
			filter.doFilter(new MockHttpServletRequest("OPTIONS", "/api/events"), new MockHttpServletResponse(), preflight);
			assertThat(preflight.getRequest()).isNotNull();
		}
	}

	@Test
	void retryAfterIsCeilingSecondsAndAtLeastOne() {
		long second = TimeUnit.SECONDS.toNanos(1);
		assertThat(RateLimitFilter.retryAfterSeconds(ConsumptionProbe.rejected(0, 0, 0))).isEqualTo(1);
		assertThat(RateLimitFilter.retryAfterSeconds(ConsumptionProbe.rejected(0, 1, 0))).isEqualTo(1);
		assertThat(RateLimitFilter.retryAfterSeconds(ConsumptionProbe.rejected(0, second, 0))).isEqualTo(1);
		assertThat(RateLimitFilter.retryAfterSeconds(ConsumptionProbe.rejected(0, second + 1, 0))).isEqualTo(2);
		assertThat(RateLimitFilter.retryAfterSeconds(ConsumptionProbe.rejected(0, 60 * second, 0))).isEqualTo(60);
	}

	private MockHttpServletResponse send(String deviceId, String ip) throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/events");
		request.setRemoteAddr(ip);
		request.setAttribute(DeviceIdFilter.DEVICE_ID_ATTRIBUTE, deviceId);
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();
		filter.doFilter(request, response, chain);
		if (response.getStatus() == 200) {
			assertThat(chain.getRequest()).isNotNull();
		}
		return response;
	}

	private static void assertPassed(MockHttpServletResponse response) {
		assertThat(response.getStatus()).isEqualTo(200);
	}

}
