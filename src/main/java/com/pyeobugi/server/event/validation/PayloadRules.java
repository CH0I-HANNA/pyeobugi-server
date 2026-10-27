package com.pyeobugi.server.event.validation;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import com.pyeobugi.server.common.time.KstTimeUtils;
import com.pyeobugi.server.event.domain.EventType;

import tools.jackson.databind.JsonNode;

/**
 * 타입별 payload 화이트리스트와 값 규칙 (mock validate.ts의 PAYLOAD_FIELDS와 동일).
 * 정의된 필드는 모두 필수이고, 값이 없거나 null이면 규칙을 통과하지 못한다.
 */
final class PayloadRules {

	static final Set<String> SEVERITIES = Set.of("MILD", "MODERATE", "SEVERE");
	static final Set<String> BUBBLE_TYPES = Set.of("POSTURE", "STRETCH", "TIMER_BREAK", "ENCOURAGE", "GREETING");

	private static final Predicate<JsonNode> KST_DATE_TIME = n -> KstTimeUtils.parseDateTime(text(n)).isPresent();
	private static final Predicate<JsonNode> BOOLEAN = n -> n != null && n.isBoolean();
	/** JS Number.isInteger와 같이 60.0처럼 소수부가 0인 숫자도 정수로 본다 */
	private static final Predicate<JsonNode> INTEGER =
			n -> n != null && n.isNumber() && n.canConvertToExactIntegral() && n.canConvertToLong();

	private static final Map<EventType, Map<String, Predicate<JsonNode>>> FIELDS = new EnumMap<>(EventType.class);

	static {
		FIELDS.put(EventType.SESSION_START, Map.of());
		FIELDS.put(EventType.SESSION_END, Map.of());
		FIELDS.put(EventType.POMODORO_COMPLETED, Map.of());
		FIELDS.put(EventType.USAGE_SEGMENT, Map.of(
				"startedAt", KST_DATE_TIME,
				"endedAt", KST_DATE_TIME,
				"durationSec", INTEGER,
				"badDurationSec", INTEGER));
		FIELDS.put(EventType.POSTURE_BAD, Map.of("severity", oneOf(SEVERITIES)));
		FIELDS.put(EventType.CALIBRATION_COMPLETED, Map.of("isRecalibration", BOOLEAN));
		FIELDS.put(EventType.BUBBLE_SHOWN, Map.of("bubbleType", oneOf(BUBBLE_TYPES)));
		FIELDS.put(EventType.SETTINGS_CHANGED, Map.of(
				"studyTimer", BOOLEAN,
				"stretch", BOOLEAN,
				"awayPause", BOOLEAN,
				"overlay", BOOLEAN));
	}

	private PayloadRules() {
	}

	static Map<String, Predicate<JsonNode>> of(EventType type) {
		return FIELDS.get(type);
	}

	static String text(JsonNode node) {
		return node != null && node.isString() ? node.stringValue() : null;
	}

	private static Predicate<JsonNode> oneOf(Set<String> values) {
		// Set.of는 contains(null)에서 예외를 던지므로 문자열이 아닌 값을 먼저 거른다
		return n -> {
			String value = text(n);
			return value != null && values.contains(value);
		};
	}

}
