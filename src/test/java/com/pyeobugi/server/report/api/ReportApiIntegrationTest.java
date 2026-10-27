package com.pyeobugi.server.report.api;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.pyeobugi.server.TestcontainersConfiguration;
import com.pyeobugi.server.common.filter.DeviceIdFilter;
import com.pyeobugi.server.common.time.KstTimeUtils;
import com.pyeobugi.server.event.api.PostEventsResponse;
import com.pyeobugi.server.event.service.EventService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * GET /api/reports/summary 전체 흐름. 현재 시각은 2026-10-08(목) 21:00 KST로 고정.
 * 이번 주는 10-05(월) ~ 10-11(일).
 */
@Import({TestcontainersConfiguration.class, ReportApiIntegrationTest.ThursdayClock.class})
@SpringBootTest
@AutoConfigureMockMvc
class ReportApiIntegrationTest {

	private static final JsonMapper MAPPER = JsonMapper.builder().build();
	private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss'+09:00'");
	private static final String SESSION_ID = "a9a9a9a9-0000-4000-8000-0000000000aa";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	EventService eventService;

	private final String deviceId = UUID.randomUUID().toString();
	private final List<JsonNode> pending = new ArrayList<>();
	private int seq;

	@Test
	void dayHourlyAndCommentForToday() throws Exception {
		segment("2026-10-08T09:00:00", 60, 18);
		segment("2026-10-08T09:01:00", 60, 18);       // 9시: (120 − 36) / 120 = 70
		segment("2026-10-08T14:00:00", 60, 0);        // 14시: 100
		segment("2026-10-08T20:59:30", 60, 30);       // 시 경계를 걸침 → 20시: 50, 21시: null
		event("POSTURE_BAD", "2026-10-08T09:00:30", "{\"severity\":\"MILD\"}");
		event("POSTURE_BAD", "2026-10-08T14:00:10", "{\"severity\":\"SEVERE\"}");
		event("POMODORO_COMPLETED", "2026-10-08T14:25:00", "{}");
		save();

		summary(null)
				.andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.hasData").value(true))
				.andExpect(jsonPath("$.generatedAt").value("2026-10-08T21:00:00+09:00"))
				.andExpect(jsonPath("$.date").value("2026-10-08"))
				// (240 − 66) / 240 = 72.5 → 73
				.andExpect(jsonPath("$.day.postureKeepRate").value(73))
				.andExpect(jsonPath("$.day.usageSeconds").value(240))
				.andExpect(jsonPath("$.day.turtleNeckCount").value(2))
				.andExpect(jsonPath("$.day.pomodoroCount").value(1))
				.andExpect(jsonPath("$.hourly", hasSize(24)))
				.andExpect(jsonPath("$.hourly[0].hour").value(0))
				.andExpect(jsonPath("$.hourly[23].hour").value(23))
				.andExpect(jsonPath("$.hourly[0].postureKeepRate").value(nullValue()))
				.andExpect(jsonPath("$.hourly[9].postureKeepRate").value(70))
				.andExpect(jsonPath("$.hourly[14].postureKeepRate").value(100))
				.andExpect(jsonPath("$.hourly[20].postureKeepRate").value(50))
				.andExpect(jsonPath("$.hourly[21].postureKeepRate").value(nullValue()))
				.andExpect(jsonPath("$.comment.mood").value("NORMAL"))
				.andExpect(jsonPath("$.comment.message").value("조금만 더 신경 써 볼까요?"));
	}

	@Test
	void weekIsMondayToRequestedDateAndUsesSumsNotDailyAverage() throws Exception {
		segment("2026-10-05T10:00:00", 60, 0);        // 월 100%
		segment("2026-10-07T10:00:00", 60, 60);       // 수
		segment("2026-10-07T10:01:00", 60, 60);       // 수 0%
		segment("2026-10-08T10:00:00", 60, 30);       // 목 (수요일 조회에는 빠져야 함)
		save();

		// 수요일 조회: 월 + 수 = (180 − 120) / 180 = 33 (일별 평균이라면 50)
		summary("2026-10-07")
				.andExpect(jsonPath("$.date").value("2026-10-07"))
				.andExpect(jsonPath("$.day.postureKeepRate").value(0))
				.andExpect(jsonPath("$.day.usageSeconds").value(120))
				.andExpect(jsonPath("$.week.postureKeepRate").value(33))
				.andExpect(jsonPath("$.week.usageSeconds").value(180))
				.andExpect(jsonPath("$.comment.mood").value("BAD"));

		// 오늘(목) 조회: 월 + 수 + 목 = (240 − 150) / 240 = 37.5 → 38
		summary(null)
				.andExpect(jsonPath("$.week.postureKeepRate").value(38))
				.andExpect(jsonPath("$.week.usageSeconds").value(240));
	}

	@Test
	void sundayBelongsToPreviousWeek() throws Exception {
		segment("2026-10-04T10:00:00", 60, 0);        // 일
		segment("2026-10-05T10:00:00", 60, 60);       // 월
		save();

		summary("2026-10-04")
				.andExpect(jsonPath("$.week.usageSeconds").value(60))
				.andExpect(jsonPath("$.week.postureKeepRate").value(100));
		summary("2026-10-05")
				.andExpect(jsonPath("$.week.usageSeconds").value(60))
				.andExpect(jsonPath("$.week.postureKeepRate").value(0));
	}

	@Test
	void dayWithoutRecordsHasNoDataButWeekKeepsOtherDays() throws Exception {
		segment("2026-10-05T10:00:00", 60, 6);
		save();

		summary("2026-10-06")
				.andExpect(jsonPath("$.hasData").value(false))
				.andExpect(jsonPath("$.day.postureKeepRate").value(nullValue()))
				.andExpect(jsonPath("$.day.usageSeconds").value(0))
				.andExpect(jsonPath("$.week.postureKeepRate").value(90))
				.andExpect(jsonPath("$.week.usageSeconds").value(60))
				.andExpect(jsonPath("$.hourly", hasSize(24)))
				.andExpect(jsonPath("$.hourly[*].postureKeepRate", everyItem(nullValue())))
				.andExpect(jsonPath("$.comment").value(nullValue()));
	}

	@Test
	void postureBadOnlyDayHasDataWithoutRateOrComment() throws Exception {
		event("POSTURE_BAD", "2026-10-08T10:00:00", "{\"severity\":\"MILD\"}");
		save();

		summary(null)
				.andExpect(jsonPath("$.hasData").value(true))
				.andExpect(jsonPath("$.day.turtleNeckCount").value(1))
				.andExpect(jsonPath("$.day.postureKeepRate").value(nullValue()))
				.andExpect(jsonPath("$.comment").value(nullValue()));
	}

	@Test
	void otherEventTypesDoNotCount() throws Exception {
		event("SESSION_START", "2026-10-08T10:00:00", "{}");
		event("CALIBRATION_COMPLETED", "2026-10-08T10:00:05", "{\"isRecalibration\":false}");
		event("BUBBLE_SHOWN", "2026-10-08T10:00:10", "{\"bubbleType\":\"GREETING\"}");
		save();

		summary(null).andExpect(jsonPath("$.hasData").value(false));
	}

	@Test
	void newDeviceGetsEmptyReport() throws Exception {
		summary(null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.hasData").value(false))
				.andExpect(jsonPath("$.date").value("2026-10-08"))
				.andExpect(jsonPath("$.day.postureKeepRate").value(nullValue()))
				.andExpect(jsonPath("$.day.turtleNeckCount").value(0))
				.andExpect(jsonPath("$.day.pomodoroCount").value(0))
				.andExpect(jsonPath("$.day.usageSeconds").value(0))
				.andExpect(jsonPath("$.week.postureKeepRate").value(nullValue()))
				.andExpect(jsonPath("$.yesterday.usageSeconds").value(0))
				.andExpect(jsonPath("$.lastWeek.usageSeconds").value(0))
				.andExpect(jsonPath("$.hourly", hasSize(24)))
				.andExpect(jsonPath("$.turtleNeckTimeline.occurredAt", hasSize(0)))
				.andExpect(jsonPath("$.turtleNeckTimeline.peakHours", hasSize(0)))
				.andExpect(jsonPath("$.distribution").value(nullValue()))
				.andExpect(jsonPath("$.comment").value(nullValue()));
	}

	@Test
	void futureDateGetsEmptyReportInsteadOf400() throws Exception {
		segment("2026-10-08T10:00:00", 60, 0);
		save();

		summary("2026-10-09")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.hasData").value(false))
				.andExpect(jsonPath("$.date").value("2026-10-09"))
				.andExpect(jsonPath("$.week.usageSeconds").value(0))
				.andExpect(jsonPath("$.yesterday.usageSeconds").value(0))
				.andExpect(jsonPath("$.hourly", hasSize(24)))
				.andExpect(jsonPath("$.turtleNeckTimeline.occurredAt", hasSize(0)))
				.andExpect(jsonPath("$.distribution").value(nullValue()));
	}

	@ParameterizedTest
	@ValueSource(strings = {"2026-02-30", "2026-1-5", "abc", "", "2026-10-08T00:00:00"})
	void invalidDateIs400(String date) throws Exception {
		summary(date)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	void missingDeviceIdIs400() throws Exception {
		mockMvc.perform(get("/api/reports/summary"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("DEVICE_ID_INVALID"));
	}

	@Test
	void yesterdayIsPreviousDayEvenAcrossWeekBoundary() throws Exception {
		segment("2026-10-04T10:00:00", 60, 30);       // 일
		segment("2026-10-07T10:00:00", 60, 6);        // 수
		event("POMODORO_COMPLETED", "2026-10-07T10:30:00", "{}");
		save();

		summary(null)
				.andExpect(jsonPath("$.yesterday.postureKeepRate").value(90))
				.andExpect(jsonPath("$.yesterday.usageSeconds").value(60))
				.andExpect(jsonPath("$.yesterday.pomodoroCount").value(1));
		summary("2026-10-05")
				.andExpect(jsonPath("$.yesterday.postureKeepRate").value(50));
	}

	@Test
	void lastWeekIsUpToSameWeekdayOfPreviousWeek() throws Exception {
		segment("2026-09-28T10:00:00", 60, 0);        // 지난주 월
		segment("2026-10-01T10:00:00", 60, 60);       // 지난주 목 (같은 요일, 포함)
		segment("2026-10-02T10:00:00", 60, 60);       // 지난주 금 (제외)
		event("POSTURE_BAD", "2026-10-01T10:00:10", "{\"severity\":\"MILD\"}");
		save();

		summary(null)
				.andExpect(jsonPath("$.lastWeek.usageSeconds").value(120))
				.andExpect(jsonPath("$.lastWeek.postureKeepRate").value(50))
				.andExpect(jsonPath("$.lastWeek.turtleNeckCount").value(1))
				.andExpect(jsonPath("$.week.usageSeconds").value(0));
	}

	@Test
	void turtleNeckTimelineIsSortedWithPeakHours() throws Exception {
		// 클라이언트는 나쁜 자세 시작 시각으로 되돌려 기록하므로 순서가 섞여 온다
		event("POSTURE_BAD", "2026-10-08T19:00:00", "{\"severity\":\"MILD\"}");
		event("POSTURE_BAD", "2026-10-08T14:05:40", "{\"severity\":\"MODERATE\"}");
		event("POSTURE_BAD", "2026-10-08T10:47:12", "{\"severity\":\"MILD\"}");
		event("POSTURE_BAD", "2026-10-08T14:30:00", "{\"severity\":\"SEVERE\"}");
		event("POSTURE_BAD", "2026-10-08T19:10:00", "{\"severity\":\"MILD\"}");
		event("POSTURE_BAD", "2026-10-07T14:00:00", "{\"severity\":\"MILD\"}");   // 다른 날
		save();

		summary(null)
				.andExpect(jsonPath("$.turtleNeckTimeline.occurredAt", contains(
						"2026-10-08T10:47:12+09:00", "2026-10-08T14:05:40+09:00", "2026-10-08T14:30:00+09:00",
						"2026-10-08T19:00:00+09:00", "2026-10-08T19:10:00+09:00")))
				.andExpect(jsonPath("$.turtleNeckTimeline.peakHours", contains(14, 19)));
	}

	@Test
	void distributionSplitsDayUsageIntoGoodAndTurtle() throws Exception {
		segment("2026-10-08T09:00:00", 60, 18);
		segment("2026-10-08T09:01:00", 60, 18);
		segment("2026-10-08T14:00:00", 60, 0);
		segment("2026-10-08T20:59:30", 60, 30);
		save();

		summary(null)
				.andExpect(jsonPath("$.distribution.goodSeconds").value(174))
				.andExpect(jsonPath("$.distribution.turtleSeconds").value(66))
				.andExpect(jsonPath("$.distribution.goodRate").value(73))
				.andExpect(jsonPath("$.distribution.turtleRate").value(27))
				.andExpect(jsonPath("$.distribution.mildSeconds").value(nullValue()))
				.andExpect(jsonPath("$.distribution.mildRate").value(nullValue()));
	}

	private ResultActions summary(String date) throws Exception {
		var request = get("/api/reports/summary").header(DeviceIdFilter.HEADER, deviceId);
		if (date != null) {
			request.param("date", date);
		}
		return mockMvc.perform(request);
	}

	private void segment(String startedAt, int durationSec, int badDurationSec) {
		LocalDateTime start = LocalDateTime.parse(startedAt);
		String endedAt = ISO.format(start.plusSeconds(durationSec));
		pending.add(MAPPER.readTree("""
				{"eventId":"%s","type":"USAGE_SEGMENT","sessionId":"%s","occurredAt":"%s",
				 "payload":{"startedAt":"%s","endedAt":"%s","durationSec":%d,"badDurationSec":%d}}
				""".formatted(nextEventId(), SESSION_ID, endedAt, ISO.format(start), endedAt, durationSec, badDurationSec)));
	}

	private void event(String type, String occurredAt, String payload) {
		pending.add(MAPPER.readTree("""
				{"eventId":"%s","type":"%s","sessionId":"%s","occurredAt":"%s","payload":%s}
				""".formatted(nextEventId(), type, SESSION_ID, occurredAt + "+09:00", payload)));
	}

	/** 실제 저장 경로(검증 → INSERT IGNORE)로 넣고, 전부 accepted인지 확인한다 */
	private void save() {
		PostEventsResponse result = eventService.ingest(deviceId, pending);
		if (result.accepted() != pending.size()) {
			throw new IllegalStateException("테스트 데이터가 거절됨: " + result);
		}
		pending.clear();
	}

	private String nextEventId() {
		return "b1c2d3e4-0000-4000-8000-%012d".formatted(++seq);
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class ThursdayClock {

		@Bean
		@Primary
		Clock thursdayClock() {
			return Clock.fixed(Instant.parse("2026-10-08T12:00:00Z"), KstTimeUtils.KST);
		}

	}

}
