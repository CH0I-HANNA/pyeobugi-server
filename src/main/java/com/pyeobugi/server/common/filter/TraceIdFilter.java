package com.pyeobugi.server.common.filter;

import java.io.IOException;
import java.util.HexFormat;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.pyeobugi.server.common.error.ErrorResponse;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** 요청마다 traceId(req- + 16진수 8자리)를 만들어 MDC에 넣는다. 로그와 에러 응답에 같은 값이 들어간다. */
@Component
@Order(FilterOrders.TRACE_ID)
public class TraceIdFilter extends OncePerRequestFilter {

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		MDC.put(ErrorResponse.TRACE_ID_MDC_KEY, newTraceId());
		try {
			chain.doFilter(request, response);
		} finally {
			MDC.remove(ErrorResponse.TRACE_ID_MDC_KEY);
		}
	}

	static String newTraceId() {
		return "req-" + HexFormat.of().toHexDigits(ThreadLocalRandom.current().nextInt());
	}

}
