package com.pyeobugi.server.common.filter;

import java.io.IOException;
import java.util.Locale;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.pyeobugi.server.common.UuidV4;
import com.pyeobugi.server.common.error.ErrorCode;
import com.pyeobugi.server.common.error.ErrorResponseWriter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * /api/** 요청의 X-Device-Id가 UUID v4인지 검사한다. 실패하면 400 DEVICE_ID_INVALID.
 * 통과한 기기 ID는 소문자로 바꿔 요청 속성(DEVICE_ID_ATTRIBUTE)에 담는다.
 */
@Component
@Order(FilterOrders.DEVICE_ID)
@RequiredArgsConstructor
public class DeviceIdFilter extends OncePerRequestFilter {

	public static final String HEADER = "X-Device-Id";
	public static final String DEVICE_ID_ATTRIBUTE = "deviceId";

	private final ErrorResponseWriter errorResponseWriter;

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		// CORS 프리플라이트에는 커스텀 헤더가 실리지 않는다 (plan 8-9)
		return !request.getRequestURI().startsWith("/api/")
				|| HttpMethod.OPTIONS.matches(request.getMethod());
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String header = request.getHeader(HEADER);
		if (!UuidV4.matches(header)) {
			errorResponseWriter.write(response, ErrorCode.DEVICE_ID_INVALID);
			return;
		}
		request.setAttribute(DEVICE_ID_ATTRIBUTE, header.toLowerCase(Locale.ROOT));
		chain.doFilter(request, response);
	}

}
