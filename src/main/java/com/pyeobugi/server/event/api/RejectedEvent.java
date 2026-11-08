package com.pyeobugi.server.event.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pyeobugi.server.event.domain.RejectCode;

import io.swagger.v3.oas.annotations.media.Schema;

/** eventId는 요청 값이 UUID v4일 때만 넣는다 (plan 7-2 응답 규칙 4) */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "거절된 이벤트", requiredProperties = { "index", "code" })
public record RejectedEvent(
		@Schema(description = "요청 events 배열의 위치 (0부터)", minimum = "0", example = "2")
		int index,
		@Schema(description = "요청한 eventId. 없거나 UUID v4가 아니면 생략",
				requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "b1c2d3e4-0000-4000-8000-000000000003")
		String eventId,
		@Schema(description = """
				거절 이유
				- `INVALID_PAYLOAD`: 공통 필드·payload의 형식이나 값 오류
				- `FORBIDDEN_FIELD`: 허용되지 않은 필드 포함 (다른 오류와 같이 있어도 이 값이 우선)
				- `UNKNOWN_TYPE`: 정의되지 않은 type
				- `OCCURRED_AT_OUT_OF_RANGE`: 시각이 현재 +5분 ~ 과거 30일 밖""", example = "INVALID_PAYLOAD")
		RejectCode code) {
}
