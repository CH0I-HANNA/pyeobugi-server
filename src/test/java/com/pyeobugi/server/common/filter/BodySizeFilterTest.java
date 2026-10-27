package com.pyeobugi.server.common.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.pyeobugi.server.common.error.ErrorResponseWriter;

import tools.jackson.databind.json.JsonMapper;

class BodySizeFilterTest {

	private final BodySizeFilter filter = new BodySizeFilter(new ErrorResponseWriter(JsonMapper.builder().build()));

	@Test
	void bodyAtLimitPassesAndCanBeReadAgain() throws Exception {
		byte[] body = bytes(BodySizeFilter.MAX_BODY_BYTES);
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(post(body, true), new MockHttpServletResponse(), chain);

		assertThat(chain.getRequest()).isNotNull();
		assertThat(chain.getRequest().getInputStream().readAllBytes()).isEqualTo(body);
	}

	@Test
	void bodyOverLimitWithContentLengthIsRejectedWith413() throws Exception {
		assertRejected(post(bytes(BodySizeFilter.MAX_BODY_BYTES + 1), true));
	}

	@Test
	void bodyOverLimitWithoutContentLengthIsRejectedWith413() throws Exception {
		assertRejected(post(bytes(BodySizeFilter.MAX_BODY_BYTES + 1), false));
	}

	@Test
	void nonApiPathIsNotChecked() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/other");
		request.setContent(bytes(BodySizeFilter.MAX_BODY_BYTES + 1));
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(request, new MockHttpServletResponse(), chain);

		assertThat(chain.getRequest()).isNotNull();
	}

	private void assertRejected(MockHttpServletRequest request) throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(request, response, chain);

		assertThat(chain.getRequest()).isNull();
		assertThat(response.getStatus()).isEqualTo(413);
		assertThat(response.getContentAsString()).contains("\"code\":\"PAYLOAD_TOO_LARGE\"");
	}

	private static MockHttpServletRequest post(byte[] body, boolean withContentLength) {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/events") {
			@Override
			public long getContentLengthLong() {
				return withContentLength ? super.getContentLengthLong() : -1;
			}
		};
		request.setContent(body);
		return request;
	}

	private static byte[] bytes(int size) {
		return "a".repeat(size).getBytes(StandardCharsets.UTF_8);
	}

}
