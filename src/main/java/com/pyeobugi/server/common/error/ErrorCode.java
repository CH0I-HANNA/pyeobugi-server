package com.pyeobugi.server.common.error;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 실패 응답의 code (plan 7-1). FE는 code만 보고 처리한다. */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

	DEVICE_ID_INVALID(HttpStatus.BAD_REQUEST, "X-Device-Id 헤더가 없거나 UUID v4 형식이 아닙니다."),
	INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다."),
	NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 경로입니다."),
	METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다."),
	PAYLOAD_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "요청 본문이 너무 큽니다."),
	RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

	private final HttpStatus status;
	private final String defaultMessage;

}
