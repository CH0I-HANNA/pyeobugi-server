package com.pyeobugi.server.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import com.pyeobugi.server.TestcontainersConfiguration;
import com.pyeobugi.server.common.filter.DeviceIdFilter;
import com.pyeobugi.server.common.filter.DeviceSecretFilter;
import com.pyeobugi.server.device.service.DeviceCredentialService;

import tools.jackson.databind.JsonNode;

/**
 * 리포트 예시(reports-summary*.response.json)와 비교한다.
 * 예시는 FE 10-27 수정안 이전 형식이라 today ↔ day로 바꿔 비교하고, 새 필드(date, hourly)는 비교하지 않는다.
 * hasData 기준이 바뀌었지만(전체 기간 → 요청 날짜) 두 예시에서는 결과가 같다.
 */
@Import({TestcontainersConfiguration.class, ContractExamples.ExampleClock.class})
@SpringBootTest
@AutoConfigureMockMvc
class ReportsContractTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	DeviceCredentialService deviceCredentialService;

	@Test
	void reportAfterExampleEventsMatchesMock() throws Exception {
		String deviceId = UUID.randomUUID().toString();
		String secret = deviceCredentialService.register(deviceId);
		postEvents(deviceId, secret, "post-events.request.json");
		postEvents(deviceId, secret, "post-events-mixed.request.json");

		assertMatchesOldFormat(summary(deviceId, secret), "reports-summary.response.json");
	}

	@Test
	void newDeviceReportMatchesMockEmptyReport() throws Exception {
		String deviceId = UUID.randomUUID().toString();
		String secret = deviceCredentialService.register(deviceId);

		assertMatchesOldFormat(summary(deviceId, secret), "reports-summary-empty.response.json");
	}

	private static void assertMatchesOldFormat(MockHttpServletResponse response, String expectedFile) throws Exception {
		JsonNode expected = ContractExamples.example(expectedFile);
		JsonNode expectedBody = expected.get("body");
		JsonNode actual = ContractExamples.MAPPER.readTree(response.getContentAsString());

		assertThat(response.getStatus()).isEqualTo(expected.get("status").asInt());
		assertThat(actual.get("hasData")).isEqualTo(expectedBody.get("hasData"));
		assertThat(actual.get("generatedAt")).isEqualTo(expectedBody.get("generatedAt"));
		assertThat(actual.get("day")).isEqualTo(expectedBody.get("today"));
		assertThat(actual.get("week")).isEqualTo(expectedBody.get("week"));
		assertThat(actual.get("comment")).isEqualTo(expectedBody.get("comment"));
	}

	private MockHttpServletResponse summary(String deviceId, String secret) throws Exception {
		return mockMvc.perform(get("/api/reports/summary")
						.header(DeviceIdFilter.HEADER, deviceId)
						.header(DeviceSecretFilter.HEADER, secret))
				.andReturn().getResponse();
	}

	private void postEvents(String deviceId, String secret, String requestFile) throws Exception {
		mockMvc.perform(post("/api/events")
						.header(DeviceIdFilter.HEADER, deviceId)
						.header(DeviceSecretFilter.HEADER, secret)
						.contentType(MediaType.APPLICATION_JSON)
						.content(ContractExamples.read(requestFile)))
				.andExpect(status().isOk());
	}

}
