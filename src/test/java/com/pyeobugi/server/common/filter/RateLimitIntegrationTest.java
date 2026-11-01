package com.pyeobugi.server.common.filter;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.pyeobugi.server.TestcontainersConfiguration;
import com.pyeobugi.server.device.service.DeviceCredentialService;

/** 설정값(pyeobugi.rate-limit)이 실제 필터 체인에 적용되는지 확인. 한도를 작게 바꿔서 띄운다. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
		"pyeobugi.rate-limit.device.capacity=3",
		"pyeobugi.rate-limit.device.refill-per-minute=1",
		"pyeobugi.rate-limit.ip.capacity=5",
		"pyeobugi.rate-limit.ip.refill-per-minute=1"
})
@AutoConfigureMockMvc
class RateLimitIntegrationTest {

	private static final String DEVICE_A = "3f2b8c1e-6a4d-4e0b-9b7a-0c1d2e3f4a5b";
	private static final String DEVICE_B = "aaaaaaaa-0000-4000-8000-000000000000";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	DeviceCredentialService deviceCredentialService;

	@Test
	void deviceThenIpLimitsReturn429WithRetryAfter() throws Exception {
		String secretA = deviceCredentialService.register(DEVICE_A);
		String secretB = deviceCredentialService.register(DEVICE_B);

		// 같은 IP(127.0.0.1)에서: 기기 A 3회 통과, 4회째 기기 한도 초과
		for (int i = 0; i < 3; i++) {
			summary(DEVICE_A, secretA).andExpect(status().isOk());
		}
		assertRateLimited(summary(DEVICE_A, secretA));

		// 기기 B 1회 통과(IP 5번째 토큰), 다음 요청은 IP 한도 초과
		summary(DEVICE_B, secretB).andExpect(status().isOk());
		assertRateLimited(summary(DEVICE_B, secretB));
	}

	private static void assertRateLimited(ResultActions result) throws Exception {
		result.andExpect(status().isTooManyRequests())
				.andExpect(header().string("Retry-After", matchesPattern("^[1-9]\\d*$")))
				.andExpect(jsonPath("$.code").value("RATE_LIMITED"))
				.andExpect(jsonPath("$.retryAfter").isNumber())
				.andExpect(jsonPath("$.traceId").value(matchesPattern("^req-[0-9a-f]{8}$")));
	}

	private ResultActions summary(String deviceId, String secret) throws Exception {
		return mockMvc.perform(get("/api/reports/summary")
				.header(DeviceIdFilter.HEADER, deviceId)
				.header(DeviceSecretFilter.HEADER, secret));
	}

}
