package com.pyeobugi.server.event.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pyeobugi.server.event.domain.RejectCode;

/** eventId는 요청 값이 UUID v4일 때만 넣는다 (plan 7-2 응답 규칙 4) */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RejectedEvent(int index, String eventId, RejectCode code) {
}
