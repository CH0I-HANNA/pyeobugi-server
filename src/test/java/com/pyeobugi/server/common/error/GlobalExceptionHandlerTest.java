package com.pyeobugi.server.common.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest
@Import(GlobalExceptionHandlerTest.TestController.class)
class GlobalExceptionHandlerTest {

	@Autowired
	MockMvc mockMvc;

	@AfterEach
	void clearMdc() {
		MDC.clear();
	}

	@Test
	void apiExceptionUsesItsCodeAndStatus() throws Exception {
		MDC.put(ErrorResponse.TRACE_ID_MDC_KEY, "req-7f3a9c2e");

		mockMvc.perform(get("/test/api-exception"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("DEVICE_ID_INVALID"))
				.andExpect(jsonPath("$.message").isNotEmpty())
				.andExpect(jsonPath("$.traceId").value("req-7f3a9c2e"))
				.andExpect(jsonPath("$.retryAfter").doesNotExist());
	}

	@Test
	void unexpectedExceptionBecomesInternalErrorWithoutLeakingMessage() throws Exception {
		mockMvc.perform(get("/test/unexpected"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
				.andExpect(jsonPath("$.message").value(ErrorCode.INTERNAL_ERROR.getDefaultMessage()));
	}

	@Test
	void malformedJsonBecomesInvalidRequest() throws Exception {
		mockMvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON).content("{\"events\": ["))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	void wrongParamTypeBecomesInvalidRequest() throws Exception {
		mockMvc.perform(get("/test/param").param("n", "abc"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	void unknownPathBecomesNotFound() throws Exception {
		mockMvc.perform(get("/test/nowhere"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}

	@Test
	void wrongMethodBecomesMethodNotAllowedAndKeepsAllowHeader() throws Exception {
		mockMvc.perform(post("/test/unexpected"))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(header().string("Allow", "GET"))
				.andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
	}

	@RestController
	static class TestController {

		@GetMapping("/test/api-exception")
		void apiException() {
			throw new ApiException(ErrorCode.DEVICE_ID_INVALID);
		}

		@GetMapping("/test/unexpected")
		void unexpected() {
			throw new IllegalStateException("내부 정보가 담긴 메시지");
		}

		@PostMapping("/test/body")
		void body(@RequestBody Object body) {
		}

		@GetMapping("/test/param")
		void param(@RequestParam int n) {
		}

	}

}
