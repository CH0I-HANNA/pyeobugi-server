package com.pyeobugi.server.common.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.pyeobugi.server.common.error.ErrorResponse;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

class TraceIdFilterTest {

	private final TraceIdFilter filter = new TraceIdFilter();

	@Test
	void putsTraceIdInMdcDuringRequestAndRemovesAfter() throws Exception {
		List<String> seen = new ArrayList<>();

		filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chainRecording(seen));

		assertThat(seen).singleElement().asString().matches("^req-[0-9a-f]{8}$");
		assertThat(MDC.get(ErrorResponse.TRACE_ID_MDC_KEY)).isNull();
	}

	@Test
	void eachRequestGetsNewTraceId() throws Exception {
		List<String> seen = new ArrayList<>();

		filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chainRecording(seen));
		filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chainRecording(seen));

		assertThat(seen).hasSize(2).doesNotHaveDuplicates();
	}

	private static MockFilterChain chainRecording(List<String> seen) {
		return new MockFilterChain(new HttpServlet() {
			@Override
			protected void service(HttpServletRequest req, HttpServletResponse res) {
				seen.add(MDC.get(ErrorResponse.TRACE_ID_MDC_KEY));
			}
		});
	}

}
