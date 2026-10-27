package com.pyeobugi.server.common.error;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
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

@WebMvcTest(controllers = GlobalExceptionHandlerTest.TestController.class)
@Import({GlobalExceptionHandlerTest.TestController.class, ErrorResponseWriter.class})
class GlobalExceptionHandlerTest {

	@Autowired
	MockMvc mockMvc;

	@Test
	void apiExceptionUsesItsCodeAndStatus() throws Exception {
		// traceId는 슬라이스 테스트에도 함께 뜨는 TraceIdFilter가 만든다
		mockMvc.perform(get("/test/api-exception"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("DEVICE_ID_INVALID"))
				.andExpect(jsonPath("$.message").isNotEmpty())
				.andExpect(jsonPath("$.traceId").value(matchesPattern("^req-[0-9a-f]{8}$")))
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
