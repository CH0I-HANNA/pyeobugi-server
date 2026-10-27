package com.pyeobugi.server.common.error;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import lombok.extern.slf4j.Slf4j;

/**
 * 컨트롤러까지 온 요청의 예외를 ErrorResponse로 바꾼다.
 * 필터에서 난 오류는 여기까지 오지 않으므로 ErrorResponseWriter로 직접 응답한다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	@ExceptionHandler(ApiException.class)
	public ResponseEntity<ErrorResponse> handleApiException(ApiException e) {
		ErrorCode code = e.getErrorCode();
		return ResponseEntity.status(code.getStatus()).body(ErrorResponse.of(code, e.getMessage()));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
		log.error("처리되지 않은 예외", e);
		ErrorCode code = ErrorCode.INTERNAL_ERROR;
		return ResponseEntity.status(code.getStatus()).body(ErrorResponse.of(code));
	}

	/**
	 * Spring MVC 표준 예외(JSON 파싱 실패, 파라미터 타입 오류, 404, 405 등)를 상태 코드 기준으로 매핑한다.
	 * 405의 Allow 같은 헤더는 그대로 유지한다.
	 */
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
			HttpStatusCode statusCode, WebRequest request) {
		ErrorCode code = toErrorCode(statusCode);
		if (statusCode.is5xxServerError()) {
			log.error("Spring MVC 처리 중 서버 오류", ex);
		}
		return ResponseEntity.status(statusCode).headers(headers).body(ErrorResponse.of(code));
	}

	private static ErrorCode toErrorCode(HttpStatusCode statusCode) {
		return switch (statusCode.value()) {
			case 404 -> ErrorCode.NOT_FOUND;
			case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
			case 413 -> ErrorCode.PAYLOAD_TOO_LARGE;
			default -> statusCode.is5xxServerError() ? ErrorCode.INTERNAL_ERROR : ErrorCode.INVALID_REQUEST;
		};
	}

}
