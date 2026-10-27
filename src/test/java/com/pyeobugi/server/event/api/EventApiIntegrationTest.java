package com.pyeobugi.server.event.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.pyeobugi.server.FixedClockConfiguration;
import com.pyeobugi.server.TestcontainersConfiguration;
import com.pyeobugi.server.common.filter.BodySizeFilter;
import com.pyeobugi.server.common.filter.DeviceIdFilter;

/** POST /api/events 전체 흐름: 필터 → 최상위 구조 검증 → 이벤트별 검증 → 저장 */
@Import({TestcontainersConfiguration.class, FixedClockConfiguration.class})
@SpringBootTest
@AutoConfigureMockMvc
class EventApiIntegrationTest {

	private static final String SESSION_ID = "a9a9a9a9-0000-4000-8000-0000000000aa";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	private final String deviceId = UUID.randomUUID().toString();

	@Test
	void validBatchReturnsCounts() throws Exception {
		postBatch(batch(sessionStart(1), sessionStart(2)))
				.andExpect(status().isOk())
				.andExpect(content().json("{\"accepted\":2,\"duplicated\":0,\"rejected\":[]}", true));
	}

	@Test
	void rejectedEventOmitsInvalidEventId() throws Exception {
		String badId = sessionStart(1).replace(eventId(1), "not-a-uuid");

		postBatch(batch(badId))
				.andExpect(status().isOk())
				.andExpect(content().json(
						"{\"accepted\":0,\"duplicated\":0,\"rejected\":[{\"index\":0,\"code\":\"INVALID_PAYLOAD\"}]}", true));
	}

	@Test
	void upperCaseDeviceIdIsStoredInLowerCase() throws Exception {
		mockMvc.perform(post("/api/events").header(DeviceIdFilter.HEADER, deviceId.toUpperCase())
						.contentType(MediaType.APPLICATION_JSON).content(batch(sessionStart(1))))
				.andExpect(status().isOk());

		assertThat(jdbcTemplate.queryForObject("SELECT device_id FROM events WHERE device_id = ?",
				String.class, deviceId)).isEqualTo(deviceId);
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"{\"events\":[],\"extra\":1}",   // 최상위 모르는 필드
			"{\"events\":[]}",               // 빈 배열
			"{\"events\":{}}",               // 배열 아님
			"{}",                            // events 없음
			"[]",                            // 객체 아님
			"null",
			"{\"events\": ["                 // 깨진 JSON
	})
	void brokenTopLevelIsInvalidRequest(String body) throws Exception {
		postBatch(body)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
				.andExpect(jsonPath("$.traceId").value(matchesPattern("^req-[0-9a-f]{8}$")));
	}

	@Test
	void hundredEventsAreAcceptedButHundredOneIs413() throws Exception {
		postBatch(batch(IntStream.rangeClosed(1, 100).mapToObj(EventApiIntegrationTest::sessionStart).toArray(String[]::new)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accepted").value(100));

		postBatch(batch(IntStream.rangeClosed(101, 201).mapToObj(EventApiIntegrationTest::sessionStart).toArray(String[]::new)))
				.andExpect(status().isContentTooLarge())
				.andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));
	}

	@Test
	void bodyOver64KbIs413BeforeParsing() throws Exception {
		String padding = " ".repeat(BodySizeFilter.MAX_BODY_BYTES);

		postBatch(batch(sessionStart(1)) + padding)
				.andExpect(status().isContentTooLarge())
				.andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));
	}

	@Test
	void missingDeviceIdIs400() throws Exception {
		mockMvc.perform(post("/api/events").contentType(MediaType.APPLICATION_JSON).content(batch(sessionStart(1))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("DEVICE_ID_INVALID"));
	}

	private ResultActions postBatch(String body) throws Exception {
		return mockMvc.perform(post("/api/events")
				.header(DeviceIdFilter.HEADER, deviceId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body));
	}

	private static String batch(String... events) {
		return IntStream.range(0, events.length).mapToObj(i -> events[i])
				.collect(Collectors.joining(",", "{\"events\":[", "]}"));
	}

	private static String eventId(int n) {
		return "b1c2d3e4-0000-4000-8000-%012d".formatted(n);
	}

	private static String sessionStart(int n) {
		return """
				{"eventId":"%s","type":"SESSION_START","sessionId":"%s","occurredAt":"2026-10-05T00:09:00+09:00","payload":{}}"""
				.formatted(eventId(n), SESSION_ID);
	}

}
