package com.pyeobugi.server.event.domain;

/** POST /api/events 응답의 rejected[].code (plan 7-2) */
public enum RejectCode {

	INVALID_PAYLOAD,
	FORBIDDEN_FIELD,
	UNKNOWN_TYPE,
	OCCURRED_AT_OUT_OF_RANGE

}
