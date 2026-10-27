package com.pyeobugi.server.event.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 검증을 통과해 저장할 수 있는 이벤트. 시각은 모두 KST.
 * kstDate·kstHour는 USAGE_SEGMENT는 startedAt, 나머지는 occurredAt 기준 (plan 8-4).
 */
public record ValidatedEvent(
		String eventId,
		String sessionId,
		EventType type,
		LocalDateTime occurredAt,
		LocalDate kstDate,
		int kstHour,
		Integer durationSec,
		Integer badDurationSec,
		Integer mildDurationSec,
		String payloadJson) {
}
