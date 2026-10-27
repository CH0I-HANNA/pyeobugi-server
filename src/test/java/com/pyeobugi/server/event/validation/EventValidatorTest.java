package com.pyeobugi.server.event.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.pyeobugi.server.event.domain.EventType;
import com.pyeobugi.server.event.domain.RejectCode;
import com.pyeobugi.server.event.domain.ValidatedEvent;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class EventValidatorTest {

	private static final JsonMapper MAPPER = JsonMapper.builder().build();
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 5, 0, 10, 0);
	private static final String EVENT_ID = "b1c2d3e4-0000-4000-8000-000000000003";
	private static final String SESSION_ID = "0f0e0d0c-0b0a-4908-8706-050403020100";

	private final EventValidator validator = new EventValidator();

	/** mock validate.test.ts와 같은 사례 */
	@Nested
	class SameAsMock {

		@Test
		void acceptsSegmentEndingExactlyAtNextKstMidnight() {
			ValidatedEvent e = valid(segment("2026-10-04T23:59:00+09:00", "2026-10-05T00:00:00+09:00", 60, 12));

			assertThat(e.type()).isEqualTo(EventType.USAGE_SEGMENT);
			assertThat(e.kstDate()).isEqualTo(LocalDate.of(2026, 10, 4));
			assertThat(e.kstHour()).isEqualTo(23);
			assertThat(e.durationSec()).isEqualTo(60);
			assertThat(e.badDurationSec()).isEqualTo(12);
			assertThat(e.mildDurationSec()).isNull();
			assertThat(e.occurredAt()).isEqualTo(LocalDateTime.of(2026, 10, 5, 0, 0, 0));
		}

		@Test
		void rejectsSegmentsThatBreakSpecRules() {
			String start = "2026-10-04T23:59:00+09:00";
			String end = "2026-10-05T00:00:00+09:00";
			assertRejected(segment(start, end, 60, 61), RejectCode.INVALID_PAYLOAD);
			assertRejected(segment(start, end, 58, 12), RejectCode.INVALID_PAYLOAD);
			assertRejected(segment(start, end, 60, 12).put("occurredAt", "2026-10-04T23:59:59+09:00"),
					RejectCode.INVALID_PAYLOAD);
			assertRejected(segment(start, "2026-10-05T00:00:30+09:00", 90, 12), RejectCode.INVALID_PAYLOAD);
		}

		@Test
		void rejectsUnknownTypesExtraFieldsAndOutOfRangeTimes() {
			assertRejected(simple("BUBBLE_REACTED", "{}"), RejectCode.UNKNOWN_TYPE);
			assertRejected(simple("SESSION_START", "{\"pitch\":12}"), RejectCode.FORBIDDEN_FIELD);
			assertRejected(simple("SESSION_START", "{}").put("faceImage", "x"), RejectCode.FORBIDDEN_FIELD);
			assertRejected(simple("SESSION_START", "{}").put("occurredAt", "2026-10-05T00:16:00+09:00"),
					RejectCode.OCCURRED_AT_OUT_OF_RANGE);
		}

	}

	@Nested
	class ForbiddenFieldFirst {

		@Test
		void winsOverInvalidEventId() {
			assertRejected(simple("SESSION_START", "{\"pitch\":12}").put("eventId", "nope"), RejectCode.FORBIDDEN_FIELD);
		}

		@Test
		void winsOverOutOfRange() {
			assertRejected(simple("SESSION_START", "{\"pitch\":12}").put("occurredAt", "2026-11-01T00:00:00+09:00"),
					RejectCode.FORBIDDEN_FIELD);
		}

		@Test
		void winsOverSegmentRuleViolation() {
			ObjectNode e = segment("2026-10-04T23:59:00+09:00", "2026-10-05T00:00:00+09:00", 58, 12);
			((ObjectNode) e.get("payload")).put("pitch", 12.5);
			assertRejected(e, RejectCode.FORBIDDEN_FIELD);
		}

		@Test
		void cannotBeJudgedForUnknownType() {
			assertRejected(simple("BUBBLE_REACTED", "{\"pitch\":12}"), RejectCode.UNKNOWN_TYPE);
		}

	}

	@Nested
	class CommonFields {

		@ParameterizedTest
		@ValueSource(strings = {"[]", "\"text\"", "null", "1"})
		void nonObjectEventIsInvalidPayload(String json) {
			assertRejected(read(json), RejectCode.INVALID_PAYLOAD);
		}

		@Test
		void invalidOrMissingIdsAreInvalidPayload() {
			assertRejected(simple("SESSION_START", "{}").put("eventId", "not-a-uuid"), RejectCode.INVALID_PAYLOAD);
			assertRejected(simple("SESSION_START", "{}").put("sessionId", "3f2b8c1e-6a4d-1e0b-9b7a-0c1d2e3f4a5b"),
					RejectCode.INVALID_PAYLOAD);
			ObjectNode noId = simple("SESSION_START", "{}");
			noId.remove("eventId");
			assertRejected(noId, RejectCode.INVALID_PAYLOAD);
		}

		@Test
		void upperCaseEventIdIsAcceptedAsIs() {
			assertThat(valid(simple("SESSION_START", "{}").put("eventId", EVENT_ID.toUpperCase())).eventId())
					.isEqualTo(EVENT_ID.toUpperCase());
		}

		@Test
		void invalidIdBeforeUnknownTypeLikeMock() {
			assertRejected(simple("BUBBLE_REACTED", "{}").put("eventId", "nope"), RejectCode.INVALID_PAYLOAD);
		}

		@Test
		void missingOrNonStringTypeIsUnknownType() {
			ObjectNode noType = simple("SESSION_START", "{}");
			noType.remove("type");
			assertRejected(noType, RejectCode.UNKNOWN_TYPE);
			assertRejected(simple("SESSION_START", "{}").put("type", 1), RejectCode.UNKNOWN_TYPE);
			assertRejected(simple("session_start", "{}"), RejectCode.UNKNOWN_TYPE);
		}

		@ParameterizedTest
		@ValueSource(strings = {
				"2026-10-04T15:00:00Z",
				"2026-10-05T00:00:00.000+09:00",
				"2026-10-05T00:00:00",
				"2026-10-04T15:00:00+00:00"
		})
		void nonKstOrMillisecondTimesAreInvalidPayload(String occurredAt) {
			assertRejected(simple("SESSION_START", "{}").put("occurredAt", occurredAt), RejectCode.INVALID_PAYLOAD);
		}

		@Test
		void occurredAtRangeBoundaries() {
			assertThat(validator.validate(simple("SESSION_START", "{}").put("occurredAt", "2026-10-05T00:15:00+09:00"), NOW))
					.isInstanceOf(ValidationResult.Valid.class);
			assertRejected(simple("SESSION_START", "{}").put("occurredAt", "2026-10-05T00:15:01+09:00"),
					RejectCode.OCCURRED_AT_OUT_OF_RANGE);
			assertThat(validator.validate(simple("SESSION_START", "{}").put("occurredAt", "2026-09-05T00:10:00+09:00"), NOW))
					.isInstanceOf(ValidationResult.Valid.class);
			assertRejected(simple("SESSION_START", "{}").put("occurredAt", "2026-09-05T00:09:59+09:00"),
					RejectCode.OCCURRED_AT_OUT_OF_RANGE);
		}

	}

	@Nested
	class Payloads {

		@Test
		void eachTypeAcceptsItsPayload() {
			assertThat(valid(simple("SESSION_START", "{}")).payloadJson()).isEqualTo("{}");
			assertThat(valid(simple("SESSION_END", "{}")).type()).isEqualTo(EventType.SESSION_END);
			assertThat(valid(simple("POMODORO_COMPLETED", "{}")).type()).isEqualTo(EventType.POMODORO_COMPLETED);
			assertThat(valid(simple("POSTURE_BAD", "{\"severity\":\"MODERATE\"}")).payloadJson())
					.isEqualTo("{\"severity\":\"MODERATE\"}");
			valid(simple("CALIBRATION_COMPLETED", "{\"isRecalibration\":false}"));
			valid(simple("BUBBLE_SHOWN", "{\"bubbleType\":\"GREETING\"}"));
			valid(simple("SETTINGS_CHANGED",
					"{\"studyTimer\":true,\"stretch\":false,\"awayPause\":true,\"overlay\":false}"));
		}

		@Test
		void nonSegmentEventsAreGroupedByOccurredAt() {
			ValidatedEvent e = valid(simple("POSTURE_BAD", "{\"severity\":\"MILD\"}"));

			assertThat(e.kstDate()).isEqualTo(LocalDate.of(2026, 10, 5));
			assertThat(e.kstHour()).isZero();
			assertThat(e.durationSec()).isNull();
		}

		@Test
		void missingWrongOrNullValuesAreInvalidPayload() {
			assertRejected(simple("POSTURE_BAD", "{}"), RejectCode.INVALID_PAYLOAD);
			assertRejected(simple("POSTURE_BAD", "{\"severity\":\"HIGH\"}"), RejectCode.INVALID_PAYLOAD);
			assertRejected(simple("POSTURE_BAD", "{\"severity\":null}"), RejectCode.INVALID_PAYLOAD);
			assertRejected(simple("CALIBRATION_COMPLETED", "{\"isRecalibration\":\"false\"}"), RejectCode.INVALID_PAYLOAD);
			assertRejected(simple("SETTINGS_CHANGED", "{\"studyTimer\":true}"), RejectCode.INVALID_PAYLOAD);
		}

		@Test
		void payloadMustBeObject() {
			ObjectNode noPayload = simple("SESSION_START", "{}");
			noPayload.remove("payload");
			assertRejected(noPayload, RejectCode.INVALID_PAYLOAD);
			assertRejected(simple("SESSION_START", "[]"), RejectCode.INVALID_PAYLOAD);
			assertRejected(simple("SESSION_START", "null"), RejectCode.INVALID_PAYLOAD);
		}

	}

	@Nested
	class UsageSegment {

		private static final String START = "2026-10-04T23:58:00+09:00";
		private static final String END = "2026-10-04T23:59:00+09:00";

		@Test
		void durationToleranceIsOneSecond() {
			valid(segment(START, END, 59, 0));
			valid(segment(START, END, 61, 0));
			assertRejected(segment(START, END, 62, 0), RejectCode.INVALID_PAYLOAD);
		}

		@Test
		void integralDecimalIsAcceptedLikeJsNumberIsInteger() {
			ObjectNode e = segment(START, END, 60, 0);
			((ObjectNode) e.get("payload")).put("durationSec", 60.0);
			assertThat(valid(e).durationSec()).isEqualTo(60);

			((ObjectNode) e.get("payload")).put("durationSec", 60.5);
			assertRejected(e, RejectCode.INVALID_PAYLOAD);
			((ObjectNode) e.get("payload")).put("durationSec", "60");
			assertRejected(e, RejectCode.INVALID_PAYLOAD);
		}

		@Test
		void badDurationMustBeBetweenZeroAndDuration() {
			valid(segment(START, END, 60, 60));
			assertRejected(segment(START, END, 60, -1), RejectCode.INVALID_PAYLOAD);
		}

		@Test
		void durationMustNotExceedOneHour() {
			valid(segment("2026-10-04T22:00:00+09:00", "2026-10-04T23:00:00+09:00", 3600, 0));
			assertRejected(segment("2026-10-04T22:00:00+09:00", "2026-10-04T23:00:01+09:00", 3601, 0),
					RejectCode.INVALID_PAYLOAD);
		}

		@Test
		void startMustBeBeforeEnd() {
			assertRejected(segment(END, END, 0, 0), RejectCode.INVALID_PAYLOAD);
			assertRejected(segment(END, START, 60, 0), RejectCode.INVALID_PAYLOAD);
		}

		@Test
		void segmentCrossingHourIsGroupedByStartedAtHour() {
			ValidatedEvent e = valid(segment("2026-10-04T21:59:30+09:00", "2026-10-04T22:00:30+09:00", 60, 0));

			assertThat(e.kstHour()).isEqualTo(21);
		}

		@Test
		void startedAtOutOfRangeEvenIfOccurredAtIsInRange() {
			assertRejected(segment("2026-09-05T00:09:00+09:00", "2026-09-05T00:10:00+09:00", 60, 0),
					RejectCode.OCCURRED_AT_OUT_OF_RANGE);
		}

		@Test
		void segmentTimesMustUseKstFormat() {
			assertRejected(segment("2026-10-04T14:58:00Z", END, 60, 0), RejectCode.INVALID_PAYLOAD);
		}

	}

	private ValidatedEvent valid(JsonNode event) {
		ValidationResult result = validator.validate(event, NOW);
		assertThat(result).isInstanceOf(ValidationResult.Valid.class);
		return ((ValidationResult.Valid) result).event();
	}

	private void assertRejected(JsonNode event, RejectCode expected) {
		assertThat(validator.validate(event, NOW)).isEqualTo(new ValidationResult.Rejected(expected));
	}

	private static ObjectNode simple(String type, String payloadJson) {
		ObjectNode e = MAPPER.createObjectNode();
		e.put("eventId", EVENT_ID);
		e.put("type", type);
		e.put("sessionId", SESSION_ID);
		e.put("occurredAt", "2026-10-05T00:09:00+09:00");
		e.set("payload", read(payloadJson));
		return e;
	}

	private static ObjectNode segment(String startedAt, String endedAt, long durationSec, long badDurationSec) {
		ObjectNode payload = MAPPER.createObjectNode();
		payload.put("startedAt", startedAt);
		payload.put("endedAt", endedAt);
		payload.put("durationSec", durationSec);
		payload.put("badDurationSec", badDurationSec);
		ObjectNode e = simple("USAGE_SEGMENT", "{}");
		e.put("occurredAt", endedAt);
		e.set("payload", payload);
		return e;
	}

	private static JsonNode read(String json) {
		return MAPPER.readTree(json);
	}

}
