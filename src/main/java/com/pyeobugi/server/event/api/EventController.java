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
import com.pyeobugi.server.common.error.ErrorResponse;
import com.pyeobugi.server.common.filter.DeviceIdFilter;
import com.pyeobugi.server.event.service.EventService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;

@Tag(name = "Event", description = "앱 이벤트 수집")
@RestController
@RequiredArgsConstructor
public class EventController {

	static final int MAX_EVENTS = 100;

	private final EventService eventService;

	/**
	 * 이벤트 배치 저장. 최상위 구조가 깨졌을 때만 배치 전체를 400/413으로 거절하고,
	 * 이벤트 하나의 문제는 rejected로 응답한다 (plan 7-2, 8-2).
	 */
	@Operation(summary = "이벤트 배치 저장", description = """
			앱이 쌓아 둔 이벤트를 한 번에 1~100개 보낸다. 본문은 최대 64KB.

			**이벤트 공통 필드** (이 밖의 필드가 있으면 `FORBIDDEN_FIELD`)

			| 필드 | 형식 |
			| --- | --- |
			| `eventId` | UUID v4. 같은 기기에서 같은 값이 다시 오면 저장하지 않고 `duplicated`로 센다 |
			| `type` | 아래 표의 값 |
			| `sessionId` | UUID v4 |
			| `occurredAt` | KST 시각 `+09:00`, 초 단위. 현재 +5분 ~ 과거 30일 |
			| `payload` | 타입별 객체. 필드가 모두 있어야 하고, 정의되지 않은 필드는 `FORBIDDEN_FIELD` |

			**타입별 payload**

			| type | payload |
			| --- | --- |
			| `SESSION_START`, `SESSION_END`, `POMODORO_COMPLETED` | `{}` |
			| `USAGE_SEGMENT` | `startedAt`, `endedAt`, `durationSec`, `badDurationSec` |
			| `POSTURE_BAD` | `severity`: `MILD`, `MODERATE`, `SEVERE` |
			| `CALIBRATION_COMPLETED` | `isRecalibration`: boolean |
			| `BUBBLE_SHOWN` | `bubbleType`: `POSTURE`, `STRETCH`, `TIMER_BREAK`, `ENCOURAGE`, `GREETING` |
			| `SETTINGS_CHANGED` | `studyTimer`, `stretch`, `awayPause`, `overlay`: 모두 boolean |

			**`USAGE_SEGMENT` 추가 규칙** (위반 시 `INVALID_PAYLOAD`)
			- `0 ≤ badDurationSec ≤ durationSec ≤ 3600`
			- `startedAt < endedAt`, 같은 KST 날짜 (자정 종료는 다음 날 `00:00:00` 허용)
			- `durationSec = endedAt − startedAt` (±1초), `occurredAt = endedAt`

			**응답 규칙**
			- `accepted + duplicated + rejected 개수 = 요청 이벤트 수`
			- 이벤트 하나에 문제가 있어도 배치 전체를 거절하지 않고 그 이벤트만 `rejected`에 넣는다.
			- `accepted`는 DB 저장까지 끝난 수라 바로 리포트에 반영된다.
			""",
			requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
					description = "`events` 필드 하나만 가진 객체",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							schema = @Schema(type = "object"),
							examples = @ExampleObject(name = "USAGE_SEGMENT와 POSTURE_BAD", value = """
									{
									  "events": [
									    {
									      "eventId": "b1c2d3e4-0000-4000-8000-000000000003",
									      "type": "USAGE_SEGMENT",
									      "sessionId": "a9a9a9a9-0000-4000-8000-0000000000aa",
									      "occurredAt": "2026-10-04T09:23:00+09:00",
									      "payload": {
									        "startedAt": "2026-10-04T09:22:00+09:00",
									        "endedAt": "2026-10-04T09:23:00+09:00",
									        "durationSec": 60,
									        "badDurationSec": 12
									      }
									    },
									    {
									      "eventId": "b1c2d3e4-0000-4000-8000-000000000004",
									      "type": "POSTURE_BAD",
									      "sessionId": "a9a9a9a9-0000-4000-8000-0000000000aa",
									      "occurredAt": "2026-10-04T09:22:40+09:00",
									      "payload": { "severity": "MODERATE" }
									    }
									  ]
									}"""))))
	@ApiResponse(responseCode = "200", description = "처리 완료. 거절된 이벤트가 있어도 200")
	@ApiResponse(responseCode = "400", description = "최상위 구조 오류(`events` 외 필드, 빈 배열, JSON 아님) 또는 기기 ID 오류. 배치를 폐기한다",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class),
					examples = @ExampleObject(
							value = "{\"code\":\"INVALID_REQUEST\",\"message\":\"events는 1개 이상인 배열이어야 합니다.\",\"traceId\":\"req-7f3a9c2e\"}")))
	@ApiResponse(responseCode = "401", description = "`X-Device-Secret`이 없거나 일치하지 않음",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "413", description = "이벤트 101개 이상 또는 본문 64KB 초과. 배치를 반으로 나눠 다시 보낸다",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class),
					examples = @ExampleObject(
							value = "{\"code\":\"PAYLOAD_TOO_LARGE\",\"message\":\"events는 최대 100개까지 보낼 수 있습니다.\",\"traceId\":\"req-7f3a9c2e\"}")))
	@ApiResponse(responseCode = "429", description = "호출 한도 초과. `retryAfter`초 후 재시도",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@PostMapping(path = "/api/events", consumes = MediaType.APPLICATION_JSON_VALUE)
	public PostEventsResponse postEvents(
			@Parameter(hidden = true) @RequestAttribute(DeviceIdFilter.DEVICE_ID_ATTRIBUTE) String deviceId,
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
