package com.pyeobugi.server.common.error;

import org.slf4j.MDC;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 모든 실패 응답의 본문: { code, message?, traceId?, retryAfter? } (plan 7-1).
 * 값이 없는 필드는 응답에서 생략한다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "실패 응답. 클라이언트는 code만 보고 처리한다", requiredProperties = { "code", "message", "traceId" })
public record ErrorResponse(
		@Schema(description = "오류 코드", allowableValues = { "DEVICE_ID_INVALID", "DEVICE_SECRET_INVALID",
				"DEVICE_ALREADY_REGISTERED", "INVALID_REQUEST", "NOT_FOUND", "METHOD_NOT_ALLOWED", "PAYLOAD_TOO_LARGE",
				"RATE_LIMITED", "INTERNAL_ERROR" }, example = "INVALID_REQUEST")
		String code,
		@Schema(description = "사람이 읽는 설명. 화면에 그대로 보여 주지 않는다", example = "요청 형식이 올바르지 않습니다.")
		String message,
		@Schema(description = "요청 추적 ID. 문의할 때 같이 알려 주면 서버 로그에서 찾을 수 있다", example = "req-7f3a9c2e")
		String traceId,
		@Schema(description = "429에서만 들어가는 재시도 대기 시간(초). Retry-After 헤더와 같은 값",
				requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "3")
		Integer retryAfter) {

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
