package com.pyeobugi.server.event.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.pyeobugi.server.FixedClockConfiguration;
import com.pyeobugi.server.TestcontainersConfiguration;
import com.pyeobugi.server.event.api.PostEventsResponse;
import com.pyeobugi.server.event.api.RejectedEvent;
import com.pyeobugi.server.event.domain.RejectCode;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 실제 MySQL에서 INSERT IGNORE 반환값(1/0)으로 accepted·duplicated가 계산되는지 확인 (plan 8-3) */
@Import({TestcontainersConfiguration.class, FixedClockConfiguration.class})
@SpringBootTest
class EventServiceTest {

	private static final JsonMapper MAPPER = JsonMapper.builder().build();
	private static final String SESSION_ID = "a9a9a9a9-0000-4000-8000-0000000000aa";

	@Autowired
	EventService eventService;

	@Autowired
	JdbcTemplate jdbcTemplate;

	/** 테스트끼리 데이터가 섞이지 않게 테스트마다 새 기기 ID를 쓴다 */
	private final String deviceId = UUID.randomUUID().toString();

	@Test
	void newEventsAreAcceptedAndStoredWithAggregationColumns() {
		PostEventsResponse result = eventService.ingest(deviceId, List.of(
				segment(1, "2026-10-04T23:59:00+09:00", "2026-10-05T00:00:00+09:00", 60, 12),
				posture(2, "MODERATE")));

		assertThat(result).isEqualTo(new PostEventsResponse(2, 0, List.of()));
		Map<String, Object> row = jdbcTemplate.queryForMap(
				"SELECT * FROM events WHERE device_id = ? AND event_id = ?", deviceId, eventId(1));
		assertThat(row.get("type")).isEqualTo("USAGE_SEGMENT");
		assertThat(((Date) row.get("kst_date")).toLocalDate()).isEqualTo(LocalDate.of(2026, 10, 4));
		assertThat(((Number) row.get("kst_hour")).intValue()).isEqualTo(23);
		assertThat(row.get("duration_sec")).isEqualTo(60);
		assertThat(row.get("bad_duration_sec")).isEqualTo(12);
		assertThat(row.get("mild_duration_sec")).isNull();
		assertThat(row.get("occurred_at")).isEqualTo(LocalDateTime.of(2026, 10, 5, 0, 0, 0));
		assertThat(row.get("received_at")).isEqualTo(LocalDateTime.of(2026, 10, 5, 0, 10, 0));
		assertThat((String) row.get("payload")).contains("\"durationSec\": 60");

		Map<String, Object> bad = jdbcTemplate.queryForMap(
				"SELECT * FROM events WHERE device_id = ? AND event_id = ?", deviceId, eventId(2));
		assertThat(bad.get("duration_sec")).isNull();
		assertThat(((Number) bad.get("kst_hour")).intValue()).isZero();
	}

	@Test
	void resentBatchIsAllDuplicated() {
		List<JsonNode> batch = List.of(sessionStart(1), sessionStart(2));
		eventService.ingest(deviceId, batch);

		PostEventsResponse resent = eventService.ingest(deviceId, batch);

		assertThat(resent).isEqualTo(new PostEventsResponse(0, 2, List.of()));
		assertThat(count()).isEqualTo(2);
	}

	@Test
	void sameEventIdWithDifferentContentIsDuplicatedAndNotOverwritten() {
		eventService.ingest(deviceId, List.of(posture(1, "MILD")));

		PostEventsResponse result = eventService.ingest(deviceId, List.of(posture(1, "SEVERE")));

		assertThat(result.duplicated()).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT payload->>'$.severity' FROM events WHERE device_id = ?", String.class, deviceId))
				.isEqualTo("MILD");
	}

	@Test
	void duplicateWithinSameBatchCountsFromSecond() {
		PostEventsResponse result = eventService.ingest(deviceId, List.of(sessionStart(1), sessionStart(1)));

		assertThat(result).isEqualTo(new PostEventsResponse(1, 1, List.of()));
	}

	@Test
	void countsAlwaysAddUpToRequestSize() {
		eventService.ingest(deviceId, List.of(sessionStart(1)));
		JsonNode forbidden = MAPPER.readTree("""
				{"eventId":"%s","type":"SESSION_START","sessionId":"%s",
				 "occurredAt":"2026-10-05T00:09:00+09:00","payload":{"pitch":12.5}}
				""".formatted(eventId(3), SESSION_ID));
		JsonNode notObject = MAPPER.readTree("\"x\"");

		PostEventsResponse result = eventService.ingest(deviceId,
				List.of(sessionStart(1), sessionStart(2), forbidden, notObject));

		assertThat(result.accepted()).isEqualTo(1);
		assertThat(result.duplicated()).isEqualTo(1);
		assertThat(result.rejected()).containsExactly(
				new RejectedEvent(2, eventId(3), RejectCode.FORBIDDEN_FIELD),
				new RejectedEvent(3, null, RejectCode.INVALID_PAYLOAD));
		assertThat(result.accepted() + result.duplicated() + result.rejected().size()).isEqualTo(4);
	}

	@Test
	void rejectedEventIsNotCountedAsDuplicateEvenIfIdExists() {
		eventService.ingest(deviceId, List.of(sessionStart(1)));
		JsonNode invalidWithSameId = MAPPER.readTree("""
				{"eventId":"%s","type":"SESSION_START","sessionId":"%s",
				 "occurredAt":"2026-10-05T00:09:00Z","payload":{}}
				""".formatted(eventId(1), SESSION_ID));

		PostEventsResponse result = eventService.ingest(deviceId, List.of(invalidWithSameId));

		assertThat(result.rejected()).containsExactly(new RejectedEvent(0, eventId(1), RejectCode.INVALID_PAYLOAD));
		assertThat(result.duplicated()).isZero();
	}

	@Test
	void sameEventIdOnAnotherDeviceIsAccepted() {
		eventService.ingest(deviceId, List.of(sessionStart(1)));

		PostEventsResponse other = eventService.ingest(UUID.randomUUID().toString(), List.of(sessionStart(1)));

		assertThat(other.accepted()).isEqualTo(1);
	}

	private int count() {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM events WHERE device_id = ?", Integer.class, deviceId);
	}

	private static String eventId(int n) {
		return "b1c2d3e4-0000-4000-8000-%012d".formatted(n);
	}

	private static JsonNode sessionStart(int n) {
		return MAPPER.readTree("""
				{"eventId":"%s","type":"SESSION_START","sessionId":"%s",
				 "occurredAt":"2026-10-05T00:09:00+09:00","payload":{}}
				""".formatted(eventId(n), SESSION_ID));
	}

	private static JsonNode posture(int n, String severity) {
		return MAPPER.readTree("""
				{"eventId":"%s","type":"POSTURE_BAD","sessionId":"%s",
				 "occurredAt":"2026-10-05T00:05:00+09:00","payload":{"severity":"%s"}}
				""".formatted(eventId(n), SESSION_ID, severity));
	}

	private static JsonNode segment(int n, String startedAt, String endedAt, int duration, int bad) {
		return MAPPER.readTree("""
				{"eventId":"%s","type":"USAGE_SEGMENT","sessionId":"%s","occurredAt":"%s",
				 "payload":{"startedAt":"%s","endedAt":"%s","durationSec":%d,"badDurationSec":%d}}
				""".formatted(eventId(n), SESSION_ID, endedAt, startedAt, endedAt, duration, bad));
	}

}
