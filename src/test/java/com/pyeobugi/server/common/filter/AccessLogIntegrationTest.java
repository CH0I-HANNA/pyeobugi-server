package com.pyeobugi.server.common.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.pyeobugi.server.FixedClockConfiguration;
import com.pyeobugi.server.TestcontainersConfiguration;

/** 요청 로그에 필요한 정보만 남고, 본문·전체 기기 ID·쿼리 문자열은 남지 않는지 확인 (plan 8-8) */
@Import({TestcontainersConfiguration.class, FixedClockConfiguration.class})
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class AccessLogIntegrationTest {

	private static final String DEVICE_ID = "3f2b8c1e-6a4d-4e0b-9b7a-0c1d2e3f4a5b";
	private static final String BODY_MARKER = "BODY-MARKER-12345";

	@Autowired
	MockMvc mockMvc;

	@Test
	void eventsRequestLogsSummaryWithoutBodyOrFullDeviceId(CapturedOutput output) throws Exception {
		mockMvc.perform(post("/api/events")
				.header(DeviceIdFilter.HEADER, DEVICE_ID)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"events":[{"eventId":"b1c2d3e4-0000-4000-8000-0000000000a1","type":"SESSION_START",
						 "sessionId":"a9a9a9a9-0000-4000-8000-0000000000aa","occurredAt":"2026-10-05T00:09:00+09:00",
						 "payload":{"note":"%s"}}]}
						""".formatted(BODY_MARKER)));

		assertThat(output).containsPattern("\\[req-[0-9a-f]{8}\\] .*POST /api/events 200 \\d+ms device=3f2b8c1e");
		assertThat(output).contains("events count=1 accepted=0 duplicated=0 rejected=1");
		assertThat(output).doesNotContain(BODY_MARKER).doesNotContain(DEVICE_ID);
	}

	@Test
	void rejectedByFilterIsLoggedWithoutDeviceAndQuery(CapturedOutput output) throws Exception {
		mockMvc.perform(get("/api/reports/summary").param("date", "2026-10-05").header(DeviceIdFilter.HEADER, "secret-looking-value"));

		assertThat(output).containsPattern("GET /api/reports/summary 400 \\d+ms device=-");
		assertThat(output).doesNotContain("secret-looking-value").doesNotContain("date=2026-10-05");
	}

}
