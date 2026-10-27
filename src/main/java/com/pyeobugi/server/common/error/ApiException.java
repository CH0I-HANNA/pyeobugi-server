package com.pyeobugi.server.common.error;

import lombok.Getter;

/** 서비스·컨트롤러에서 던지면 GlobalExceptionHandler가 ErrorResponse로 바꾼다. */
@Getter
public class ApiException extends RuntimeException {

	private final ErrorCode errorCode;

	public ApiException(ErrorCode errorCode) {
		this(errorCode, errorCode.getDefaultMessage());
	}

	public ApiException(ErrorCode errorCode, String message) {
		super(message);
		this.errorCode = errorCode;
	}

}
