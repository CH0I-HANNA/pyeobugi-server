package com.pyeobugi.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.pyeobugi.server.common.filter.DeviceIdFilter;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 1단계 완료 기준 (plan 13장): 실제 서버(Tomcat) + MySQL에서
 * 잘못된 X-Device-Id 요청이 400 DEVICE_ID_INVALID + traceId로 응답되는지 확인한다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SkeletonIntegrationTest {

	private static final String VALID_DEVICE_ID = "3f2b8c1e-6a4d-4e0b-9b7a-0c1d2e3f4a5b";
	private static final String TRACE_ID_FORMAT = "^req-[0-9a-f]{8}$";

	private final HttpClient httpClient = HttpClient.newHttpClient();
	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Test
	void serverIsConnectedToMigratedDatabase() {
		Integer version = jdbcTemplate.queryForObject(
				"SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE success = 1", Integer.class);
		Integer eventsTable = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'events'",
				Integer.class);

		assertThat(version).isGreaterThanOrEqualTo(1);
		assertThat(eventsTable).isEqualTo(1);
	}

	@Test
	void missingDeviceIdReturns400WithTraceId() throws Exception {
		HttpResponse<String> response = send(get("/api/reports/summary").build());

		assertDeviceIdInvalid(response);
	}

	@Test
	void invalidDeviceIdReturns400WithTraceId() throws Exception {
		HttpResponse<String> response = send(post("/api/events", "{\"events\":[]}")
				.header(DeviceIdFilter.HEADER, "not-a-uuid").build());

		assertDeviceIdInvalid(response);
	}

	@Test
	void validDeviceIdPassesFilterAndReachesDispatcher() throws Exception {
		// 아직 API가 없으므로 필터를 통과하면 404 NOT_FOUND가 나와야 한다
		HttpResponse<String> response = send(get("/api/reports/summary")
				.header(DeviceIdFilter.HEADER, VALID_DEVICE_ID).build());

		assertThat(response.statusCode()).isEqualTo(404);
		JsonNode body = jsonMapper.readTree(response.body());
		assertThat(body.get("code").asString()).isEqualTo("NOT_FOUND");
		assertThat(body.get("traceId").asString()).matches(TRACE_ID_FORMAT);
	}

	@Test
	void nonApiPathDoesNotRequireDeviceId() throws Exception {
		HttpResponse<String> response = send(get("/v3/api-docs").build());

		assertThat(response.statusCode()).isEqualTo(200);
	}

	@Test
	void eachRequestGetsDifferentTraceId() throws Exception {
		String first = jsonMapper.readTree(send(get("/api/events").build()).body()).get("traceId").asString();
		String second = jsonMapper.readTree(send(get("/api/events").build()).body()).get("traceId").asString();

		assertThat(first).isNotEqualTo(second);
	}

	private void assertDeviceIdInvalid(HttpResponse<String> response) throws Exception {
		assertThat(response.statusCode()).isEqualTo(400);
		assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
				type -> assertThat(type).startsWith("application/json"));
		JsonNode body = jsonMapper.readTree(response.body());
		assertThat(body.get("code").asString()).isEqualTo("DEVICE_ID_INVALID");
		assertThat(body.get("message").asString()).isNotBlank();
		assertThat(body.get("traceId").asString()).matches(TRACE_ID_FORMAT);
		assertThat(body.has("retryAfter")).isFalse();
	}

	private HttpRequest.Builder get(String path) {
		return HttpRequest.newBuilder(uri(path)).GET();
	}

	private HttpRequest.Builder post(String path, String json) {
		return HttpRequest.newBuilder(uri(path))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(json));
	}

	private URI uri(String path) {
		return URI.create("http://localhost:" + port + path);
	}

	private HttpResponse<String> send(HttpRequest request) throws Exception {
		return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
	}

}
