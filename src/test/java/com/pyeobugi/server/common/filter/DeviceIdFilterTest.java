package com.pyeobugi.server.common.filter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.pyeobugi.server.common.error.ErrorResponseWriter;

import tools.jackson.databind.json.JsonMapper;

class DeviceIdFilterTest {

	private static final String VALID = "3f2b8c1e-6a4d-4e0b-9b7a-0c1d2e3f4a5b";

	private final DeviceIdFilter filter = new DeviceIdFilter(new ErrorResponseWriter(JsonMapper.builder().build()));

	@Test
	void validDeviceIdPassesAndIsStoredAsAttribute() throws Exception {
		MockHttpServletRequest request = apiRequest("GET", VALID);
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(request, new MockHttpServletResponse(), chain);

		assertThat(chain.getRequest()).isNotNull();
		assertThat(request.getAttribute(DeviceIdFilter.DEVICE_ID_ATTRIBUTE)).isEqualTo(VALID);
	}

	@Test
	void upperCaseDeviceIdIsAcceptedAndNormalizedToLowerCase() throws Exception {
		MockHttpServletRequest request = apiRequest("GET", VALID.toUpperCase());
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(request, new MockHttpServletResponse(), chain);

		assertThat(chain.getRequest()).isNotNull();
		assertThat(request.getAttribute(DeviceIdFilter.DEVICE_ID_ATTRIBUTE)).isEqualTo(VALID);
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = {
			"",
			"not-a-uuid",
			"3f2b8c1e6a4d4e0b9b7a0c1d2e3f4a5b",       // 하이픈 없음
			"3f2b8c1e-6a4d-1e0b-9b7a-0c1d2e3f4a5b",   // v1
			"3f2b8c1e-6a4d-4e0b-cb7a-0c1d2e3f4a5b",   // variant 오류
			"3f2b8c1e-6a4d-4e0b-9b7a-0c1d2e3f4a5b ",  // 공백
			"{3f2b8c1e-6a4d-4e0b-9b7a-0c1d2e3f4a5b}"
	})
	void invalidDeviceIdIsRejectedWith400(String deviceId) throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(apiRequest("POST", deviceId), response, chain);

		assertThat(chain.getRequest()).isNull();
		assertThat(response.getStatus()).isEqualTo(400);
		assertThat(response.getContentAsString()).contains("\"code\":\"DEVICE_ID_INVALID\"");
	}

	@Test
	void nonApiPathIsNotChecked() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/swagger-ui/index.html");
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(request, new MockHttpServletResponse(), chain);

		assertThat(chain.getRequest()).isNotNull();
	}

	@Test
	void corsPreflightIsNotChecked() throws Exception {
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(apiRequest("OPTIONS", null), new MockHttpServletResponse(), chain);

		assertThat(chain.getRequest()).isNotNull();
	}

	private static MockHttpServletRequest apiRequest(String method, String deviceId) {
		MockHttpServletRequest request = new MockHttpServletRequest(method, "/api/events");
		if (deviceId != null) {
			request.addHeader(DeviceIdFilter.HEADER, deviceId);
		}
		return request;
	}

}
