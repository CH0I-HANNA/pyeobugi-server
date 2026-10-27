package com.pyeobugi.server.event.api;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.pyeobugi.server.common.error.ApiException;
import com.pyeobugi.server.common.error.ErrorCode;
import com.pyeobugi.server.common.filter.DeviceIdFilter;
import com.pyeobugi.server.event.service.EventService;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;

@RestController
@RequiredArgsConstructor
public class EventController {

	static final int MAX_EVENTS = 100;

	private final EventService eventService;

	/**
	 * 이벤트 배치 저장. 최상위 구조가 깨졌을 때만 배치 전체를 400/413으로 거절하고,
	 * 이벤트 하나의 문제는 rejected로 응답한다 (plan 7-2, 8-2).
	 */
	@PostMapping(path = "/api/events", consumes = MediaType.APPLICATION_JSON_VALUE)
	public PostEventsResponse postEvents(
			@RequestAttribute(DeviceIdFilter.DEVICE_ID_ATTRIBUTE) String deviceId,
			@RequestBody JsonNode body) {
		return eventService.ingest(deviceId, eventsOf(body));
	}

	/** 검사 순서는 mock handlers.ts와 같다: 최상위 필드 → events 배열·빈 배열 → 100개 초과 */
	private static List<JsonNode> eventsOf(JsonNode body) {
		if (body == null || !body.isObject() || body.propertyNames().stream().anyMatch(k -> !k.equals("events"))) {
			throw new ApiException(ErrorCode.INVALID_REQUEST, "본문은 events 필드 하나만 가진 객체여야 합니다.");
		}
		JsonNode events = body.get("events");
		if (events == null || !events.isArray() || events.isEmpty()) {
			throw new ApiException(ErrorCode.INVALID_REQUEST, "events는 1개 이상인 배열이어야 합니다.");
		}
		if (events.size() > MAX_EVENTS) {
			throw new ApiException(ErrorCode.PAYLOAD_TOO_LARGE, "events는 최대 100개까지 보낼 수 있습니다.");
		}
		return new ArrayList<>(events.values());
	}

}
