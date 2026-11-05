package com.pyeobugi.server.device.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.pyeobugi.server.FixedClockConfiguration;
import com.pyeobugi.server.TestcontainersConfiguration;
import com.pyeobugi.server.common.filter.DeviceIdFilter;
import com.pyeobugi.server.common.filter.DeviceSecretFilter;

import tools.jackson.databind.json.JsonMapper;

/**
 * 기기 ID 탈취 대응: 등록으로 받은 토큰이 있어야만 /api/** 요청이 통과하는지 확인.
 */
@Import({TestcontainersConfiguration.class, FixedClockConfiguration.class})
@SpringBootTest
@AutoConfigureMockMvc
class DeviceApiIntegrationTest {

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	@Autowired
	MockMvc mockMvc;

	@Test
	void registeredSecretAllowsAccessButWrongOrMissingSecretIsRejected() throws Exception {
		String deviceId = UUID.randomUUID().toString();

		String body = register(deviceId).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		String deviceSecret = MAPPER.readTree(body).get("deviceSecret").asString();

		summary(deviceId, deviceSecret).andExpect(status().isOk());
		summary(deviceId, "wrong-secret")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("DEVICE_SECRET_INVALID"));
		mockMvc.perform(get("/api/reports/summary").header(DeviceIdFilter.HEADER, deviceId))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("DEVICE_SECRET_INVALID"));
	}

	@Test
	void reRegisteringSameDeviceIsConflict() throws Exception {
		String deviceId = UUID.randomUUID().toString();

		register(deviceId).andExpect(status().isOk());
		register(deviceId)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DEVICE_ALREADY_REGISTERED"));
	}

	@Test
	void unregisteredDeviceIsRejectedEvenWithSomeSecret() throws Exception {
		String neverRegistered = UUID.randomUUID().toString();

		summary(neverRegistered, "any-secret-value")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("DEVICE_SECRET_INVALID"));
	}

	private ResultActions register(String deviceId) throws Exception {
		return mockMvc.perform(post("/api/devices/register").header(DeviceIdFilter.HEADER, deviceId));
	}

	private ResultActions summary(String deviceId, String secret) throws Exception {
		return mockMvc.perform(get("/api/reports/summary")
				.header(DeviceIdFilter.HEADER, deviceId)
				.header(DeviceSecretFilter.HEADER, secret));
	}

}
