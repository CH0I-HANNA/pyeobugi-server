package com.pyeobugi.server.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import com.pyeobugi.server.TestcontainersConfiguration;
import com.pyeobugi.server.common.filter.DeviceIdFilter;
import com.pyeobugi.server.common.time.KstTimeUtils;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * AI 레포 docs/api/examples/*.json을 그대로 넣었을 때 mock과 같은 응답이 나오는지 확인한다 (plan 10-2).
 * 예시 파일은 src/test/resources/contract/에 복사해 두었다.
 */
@Import({TestcontainersConfiguration.class, EventsContractTest.ExampleClock.class})
@SpringBootTest
@AutoConfigureMockMvc
class EventsContractTest {

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	@Autowired
	MockMvc mockMvc;

	@Test
	void fullSessionThenMixedBatchMatchesMockResponses() throws Exception {
		String deviceId = UUID.randomUUID().toString();

		assertMatches(postEvents(deviceId, "post-events.request.json"), "post-events.response.json");
		// mixed의 첫 이벤트는 위 요청의 SESSION_START와 같은 eventId라서 duplicated가 된다
		assertMatches(postEvents(deviceId, "post-events-mixed.request.json"), "post-events-mixed.response.json");
	}

	@Test
	void invalidDeviceIdMatchesMockStatusAndCode() throws Exception {
		MockHttpServletResponse response = postEvents("not-a-uuid", "post-events.request.json");
		JsonNode expected = example("error-device-id.response.json");

		// message와 traceId는 서버마다 달라서 비교하지 않는다 (FE는 code만 필수로 본다)
		assertThat(response.getStatus()).isEqualTo(expected.get("status").asInt());
		assertThat(MAPPER.readTree(response.getContentAsString()).get("code"))
				.isEqualTo(expected.get("body").get("code"));
	}

	private MockHttpServletResponse postEvents(String deviceId, String requestFile) throws Exception {
		return mockMvc.perform(post("/api/events")
						.header(DeviceIdFilter.HEADER, deviceId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(read(requestFile)))
				.andReturn().getResponse();
	}

	/** JSON 객체의 필드 순서는 무시하고, 배열 순서와 값은 그대로 비교한다 */
	private static void assertMatches(MockHttpServletResponse response, String expectedFile) throws Exception {
		JsonNode expected = example(expectedFile);
		assertThat(response.getStatus()).isEqualTo(expected.get("status").asInt());
		assertThat(MAPPER.readTree(response.getContentAsString())).isEqualTo(expected.get("body"));
	}

	private static JsonNode example(String file) throws IOException {
		return MAPPER.readTree(read(file));
	}

	private static String read(String file) throws IOException {
		try (InputStream in = EventsContractTest.class.getResourceAsStream("/contract/" + file)) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	/** 예시 리포트의 generatedAt과 같은 시각으로 고정한다 */
	@TestConfiguration(proxyBeanMethods = false)
	static class ExampleClock {

		@Bean
		@Primary
		Clock exampleClock() {
			return Clock.fixed(Instant.parse("2026-10-25T12:35:00Z"), KstTimeUtils.KST);
		}

	}

}
