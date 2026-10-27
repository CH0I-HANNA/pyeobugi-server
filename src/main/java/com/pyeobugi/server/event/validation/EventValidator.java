package com.pyeobugi.server.event.validation;

import static com.pyeobugi.server.event.validation.PayloadRules.text;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import org.springframework.stereotype.Component;

import com.pyeobugi.server.common.UuidV4;
import com.pyeobugi.server.common.time.KstTimeUtils;
import com.pyeobugi.server.event.domain.EventType;
import com.pyeobugi.server.event.domain.RejectCode;
import com.pyeobugi.server.event.domain.ValidatedEvent;
import com.pyeobugi.server.event.validation.ValidationResult.Rejected;
import com.pyeobugi.server.event.validation.ValidationResult.Valid;

import tools.jackson.databind.JsonNode;

/**
 * 이벤트 하나를 검증한다 (plan 8-2).
 * 순서는 mock validate.ts와 같고, FORBIDDEN_FIELD 우선 규칙(FE 10-27) 때문에 금지 필드 검사만 앞에 둔다.
 */
@Component
public class EventValidator {

	private static final Set<String> EVENT_FIELDS = Set.of("eventId", "type", "sessionId", "occurredAt", "payload");
	private static final Duration FUTURE_LIMIT = Duration.ofMinutes(5);
	private static final Duration PAST_LIMIT = Duration.ofDays(30);
	private static final long MAX_SEGMENT_SEC = 3600;

	public ValidationResult validate(JsonNode raw, LocalDateTime now) {
		// 1. 이벤트가 객체인지
		if (raw == null || !raw.isObject()) {
			return reject(RejectCode.INVALID_PAYLOAD);
		}
		// 2. 공통 필드 화이트리스트
		if (!EVENT_FIELDS.containsAll(raw.propertyNames())) {
			return reject(RejectCode.FORBIDDEN_FIELD);
		}
		Optional<EventType> type = EventType.from(text(raw.get("type")));
		JsonNode payload = raw.get("payload");
		// 3. payload 화이트리스트 (타입을 알 때만 판단 가능)
		if (type.isPresent() && payload != null && payload.isObject()
				&& !PayloadRules.of(type.get()).keySet().containsAll(payload.propertyNames())) {
			return reject(RejectCode.FORBIDDEN_FIELD);
		}
		// 4. eventId, sessionId
		String eventId = text(raw.get("eventId"));
		String sessionId = text(raw.get("sessionId"));
		if (!UuidV4.matches(eventId) || !UuidV4.matches(sessionId)) {
			return reject(RejectCode.INVALID_PAYLOAD);
		}
		// 5. type
		if (type.isEmpty()) {
			return reject(RejectCode.UNKNOWN_TYPE);
		}
		// 6~7. occurredAt 형식과 범위
		Optional<LocalDateTime> occurredAt = KstTimeUtils.parseDateTime(text(raw.get("occurredAt")));
		if (occurredAt.isEmpty()) {
			return reject(RejectCode.INVALID_PAYLOAD);
		}
		if (!inRange(occurredAt.get(), now)) {
			return reject(RejectCode.OCCURRED_AT_OUT_OF_RANGE);
		}
		// 8~9. payload 값
		if (payload == null || !payload.isObject()) {
			return reject(RejectCode.INVALID_PAYLOAD);
		}
		for (Map.Entry<String, Predicate<JsonNode>> field : PayloadRules.of(type.get()).entrySet()) {
			if (!field.getValue().test(payload.get(field.getKey()))) {
				return reject(RejectCode.INVALID_PAYLOAD);
			}
		}
		// 10~11. USAGE_SEGMENT 추가 규칙
		if (type.get() == EventType.USAGE_SEGMENT) {
			return validateSegment(eventId, sessionId, occurredAt.get(), payload, now);
		}
		LocalDateTime at = occurredAt.get();
		return new Valid(new ValidatedEvent(eventId, sessionId, type.get(), at, at.toLocalDate(), at.getHour(),
				null, null, null, payload.toString()));
	}

	private ValidationResult validateSegment(String eventId, String sessionId, LocalDateTime occurredAt,
			JsonNode payload, LocalDateTime now) {
		LocalDateTime started = KstTimeUtils.parseDateTime(text(payload.get("startedAt"))).orElseThrow();
		LocalDateTime ended = KstTimeUtils.parseDateTime(text(payload.get("endedAt"))).orElseThrow();
		if (!inRange(started, now) || !inRange(ended, now)) {
			return reject(RejectCode.OCCURRED_AT_OUT_OF_RANGE);
		}
		long duration = payload.get("durationSec").asLong();
		long bad = payload.get("badDurationSec").asLong();
		boolean endsAtNextMidnight = ended.equals(started.toLocalDate().plusDays(1).atStartOfDay());
		boolean sameDay = started.toLocalDate().equals(ended.toLocalDate()) || endsAtNextMidnight;
		boolean valid = bad >= 0
				&& bad <= duration
				&& duration <= MAX_SEGMENT_SEC
				&& started.isBefore(ended)
				&& sameDay
				&& Math.abs(duration - Duration.between(started, ended).toSeconds()) <= 1
				&& occurredAt.equals(ended);
		if (!valid) {
			return reject(RejectCode.INVALID_PAYLOAD);
		}
		return new Valid(new ValidatedEvent(eventId, sessionId, EventType.USAGE_SEGMENT, occurredAt,
				started.toLocalDate(), started.getHour(), (int) duration, (int) bad, null, payload.toString()));
	}

	/** 현재 +5분 ~ 과거 30일 (경계 포함) */
	private static boolean inRange(LocalDateTime at, LocalDateTime now) {
		return !at.isAfter(now.plus(FUTURE_LIMIT)) && !at.isBefore(now.minus(PAST_LIMIT));
	}

	private static Rejected reject(RejectCode code) {
		return new Rejected(code);
	}

}
