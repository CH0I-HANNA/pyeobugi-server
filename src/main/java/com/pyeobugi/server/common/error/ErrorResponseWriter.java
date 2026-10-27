package com.pyeobugi.server.common.error;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

/** 필터에서 ErrorResponse를 직접 응답할 때 쓴다 (DeviceIdFilter, RateLimitFilter, BodySizeFilter). */
@Component
@RequiredArgsConstructor
public class ErrorResponseWriter {

	private final JsonMapper jsonMapper;

	public void write(HttpServletResponse response, ErrorCode code) throws IOException {
		write(response, code.getStatus().value(), ErrorResponse.of(code));
	}

	public void write(HttpServletResponse response, int status, ErrorResponse body) throws IOException {
		response.setStatus(status);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		jsonMapper.writeValue(response.getOutputStream(), body);
	}

}
