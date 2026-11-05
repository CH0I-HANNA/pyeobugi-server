package com.pyeobugi.server.device.api;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

import com.pyeobugi.server.common.filter.DeviceIdFilter;
import com.pyeobugi.server.device.service.DeviceCredentialService;

import lombok.RequiredArgsConstructor;

/**
 * 기기별 비밀 토큰 발급. 발급받은 deviceSecret은 클라이언트가 저장해 두고,
 * 이후 모든 /api/** 요청에 X-Device-Secret 헤더로 같이 보내야 한다.
 */
@RestController
@RequiredArgsConstructor
public class DeviceController {

	private final DeviceCredentialService deviceCredentialService;

	@PostMapping("/api/devices/register")
	public DeviceRegisterResponse register(
			@RequestAttribute(DeviceIdFilter.DEVICE_ID_ATTRIBUTE) String deviceId) {
		return new DeviceRegisterResponse(deviceCredentialService.register(deviceId));
	}

}
