package com.pyeobugi.server.common.error;

import org.slf4j.MDC;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 모든 실패 응답의 본문: { code, message?, traceId?, retryAfter? } (plan 7-1).
 * 값이 없는 필드는 응답에서 생략한다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String code, String message, String traceId, Integer retryAfter) {

	/** TraceIdFilter가 MDC에 traceId를 넣을 때 쓰는 키 */
	public static final String TRACE_ID_MDC_KEY = "traceId";

	public static ErrorResponse of(ErrorCode code) {
		return of(code, code.getDefaultMessage());
	}

	public static ErrorResponse of(ErrorCode code, String message) {
		return new ErrorResponse(code.name(), message, MDC.get(TRACE_ID_MDC_KEY), null);
	}

	/** 429 RATE_LIMITED 전용 */
	public static ErrorResponse rateLimited(int retryAfterSeconds) {
		ErrorCode code = ErrorCode.RATE_LIMITED;
		return new ErrorResponse(code.name(), code.getDefaultMessage(), MDC.get(TRACE_ID_MDC_KEY), retryAfterSeconds);
	}

}
