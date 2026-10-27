package com.pyeobugi.server.event.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pyeobugi.server.common.UuidV4;
import com.pyeobugi.server.common.time.KstClock;
import com.pyeobugi.server.event.api.PostEventsResponse;
import com.pyeobugi.server.event.api.RejectedEvent;
import com.pyeobugi.server.event.domain.ValidatedEvent;
import com.pyeobugi.server.event.repository.EventRepository;
import com.pyeobugi.server.event.validation.EventValidator;
import com.pyeobugi.server.event.validation.ValidationResult;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;

@Service
@RequiredArgsConstructor
public class EventService {

	private final EventValidator eventValidator;
	private final EventRepository eventRepository;
	private final KstClock clock;

	/**
	 * 이벤트를 하나씩 검증하고, 통과한 것만 INSERT IGNORE로 저장한다 (plan 8-3).
	 * 메서드가 끝나며 커밋된 뒤 응답하므로 accepted는 커밋까지 끝난 수다.
	 * 같은 배치 안의 중복 eventId도 두 번째 삽입이 0을 돌려주므로 duplicated가 된다.
	 */
	@Transactional
	public PostEventsResponse ingest(String deviceId, List<JsonNode> events) {
		LocalDateTime now = clock.now();
		int accepted = 0;
		int duplicated = 0;
		List<RejectedEvent> rejected = new ArrayList<>();
		for (int index = 0; index < events.size(); index++) {
			JsonNode raw = events.get(index);
			switch (eventValidator.validate(raw, now)) {
				case ValidationResult.Rejected r -> rejected.add(new RejectedEvent(index, eventIdOf(raw), r.code()));
				case ValidationResult.Valid v -> {
					if (insert(deviceId, v.event(), now) == 1) {
						accepted++;
					} else {
						duplicated++;
					}
				}
			}
		}
		return new PostEventsResponse(accepted, duplicated, rejected);
	}

	private int insert(String deviceId, ValidatedEvent e, LocalDateTime receivedAt) {
		return eventRepository.insertIgnore(
				deviceId, e.eventId(), e.sessionId(), e.type().name(),
				e.occurredAt(), e.kstDate(), e.kstHour(),
				e.durationSec(), e.badDurationSec(), e.mildDurationSec(),
				e.payloadJson(), receivedAt);
	}

	/** 요청의 eventId가 UUID v4일 때만 그대로 돌려준다 */
	private static String eventIdOf(JsonNode raw) {
		JsonNode node = raw == null ? null : raw.get("eventId");
		String eventId = node != null && node.isString() ? node.stringValue() : null;
		return UuidV4.matches(eventId) ? eventId : null;
	}

}
