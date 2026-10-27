package com.pyeobugi.server.common.filter;

import org.springframework.core.Ordered;

/** 필터 실행 순서 (plan 8-1). traceId가 가장 먼저 있어야 이후 필터의 오류 응답에도 traceId가 들어간다. */
public final class FilterOrders {

	public static final int TRACE_ID = Ordered.HIGHEST_PRECEDENCE + 10;
	public static final int DEVICE_ID = Ordered.HIGHEST_PRECEDENCE + 20;
	public static final int RATE_LIMIT = Ordered.HIGHEST_PRECEDENCE + 30;
	public static final int BODY_SIZE = Ordered.HIGHEST_PRECEDENCE + 40;

	private FilterOrders() {
	}

}
