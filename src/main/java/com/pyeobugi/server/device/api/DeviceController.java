package com.pyeobugi.server.device.api;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

import com.pyeobugi.server.common.error.ErrorResponse;
import com.pyeobugi.server.common.filter.DeviceIdFilter;
import com.pyeobugi.server.config.OpenApiConfig;
import com.pyeobugi.server.device.service.DeviceCredentialService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 기기별 비밀 토큰 발급. 발급받은 deviceSecret은 클라이언트가 저장해 두고,
 * 이후 모든 /api/** 요청에 X-Device-Secret 헤더로 같이 보내야 한다.
 */
@Tag(name = "Device", description = "기기 등록")
@RestController
@RequiredArgsConstructor
public class DeviceController {

	private final DeviceCredentialService deviceCredentialService;

	@Operation(summary = "기기 등록 (비밀 토큰 발급)", description = """
			`X-Device-Id`의 기기에 비밀 토큰을 발급한다. 이 요청만 `X-Device-Secret` 없이 호출한다.

			- 토큰은 **한 번만** 내려준다. 서버에는 해시만 저장하므로 다시 조회할 수 없다.
			- 같은 기기 ID로 다시 호출하면 409 `DEVICE_ALREADY_REGISTERED`다.
			- 요청 본문은 없다.
			""",
			security = @SecurityRequirement(name = OpenApiConfig.DEVICE_ID))
	@ApiResponse(responseCode = "200", description = "발급 성공")
	@ApiResponse(responseCode = "400", description = "`X-Device-Id`가 없거나 UUID v4 형식이 아님",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class),
					examples = @ExampleObject(
							value = "{\"code\":\"DEVICE_ID_INVALID\",\"message\":\"X-Device-Id 헤더가 없거나 UUID v4 형식이 아닙니다.\",\"traceId\":\"req-7f3a9c2e\"}")))
	@ApiResponse(responseCode = "409", description = "이미 등록된 기기",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class),
					examples = @ExampleObject(
							value = "{\"code\":\"DEVICE_ALREADY_REGISTERED\",\"message\":\"이미 등록된 기기입니다.\",\"traceId\":\"req-7f3a9c2e\"}")))
	@ApiResponse(responseCode = "429", description = "호출 한도 초과. `retryAfter`초 후 재시도",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@PostMapping("/api/devices/register")
	public DeviceRegisterResponse register(
			@Parameter(hidden = true) @RequestAttribute(DeviceIdFilter.DEVICE_ID_ATTRIBUTE) String deviceId) {
		return new DeviceRegisterResponse(deviceCredentialService.register(deviceId));
	}

}
