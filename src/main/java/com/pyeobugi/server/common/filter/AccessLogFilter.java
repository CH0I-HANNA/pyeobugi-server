package com.pyeobugi.server.common.filter;

import java.io.IOException;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * 요청마다 한 줄 로그: 메서드, 경로, 상태 코드, 처리 시간, 기기 ID 앞 8자리 (plan 8-8).
 * traceId는 로그 패턴이 붙인다. 요청 본문, 쿼리 문자열, IP는 남기지 않는다.
 */
@Slf4j
@Component
@Order(FilterOrders.ACCESS_LOG)
public class AccessLogFilter extends OncePerRequestFilter {

	private static final int DEVICE_ID_PREFIX = 8;

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !request.getRequestURI().startsWith("/api/");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		long start = System.nanoTime();
		try {
			chain.doFilter(request, response);
		} finally {
			long millis = (System.nanoTime() - start) / 1_000_000;
			log.info("{} {} {} {}ms device={}", request.getMethod(), request.getRequestURI(), response.getStatus(),
					millis, devicePrefix(request));
		}
	}

	/** DeviceIdFilter가 검증한 기기 ID만 쓴다. 검증 전에 거절됐으면 "-" (잘못된 헤더 값은 로그에 넣지 않음) */
	private static String devicePrefix(HttpServletRequest request) {
		Object deviceId = request.getAttribute(DeviceIdFilter.DEVICE_ID_ATTRIBUTE);
		return deviceId instanceof String id ? id.substring(0, DEVICE_ID_PREFIX) : "-";
	}

}
