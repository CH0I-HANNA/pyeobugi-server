package com.pyeobugi.server.device.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "기기 등록 결과", requiredProperties = "deviceSecret")
public record DeviceRegisterResponse(
		@Schema(description = "기기 비밀 토큰 (URL-safe Base64, 43자). 한 번만 내려주므로 앱이 안전하게 저장하고, "
				+ "이후 모든 요청의 X-Device-Secret 헤더에 넣는다", example = "q3J8x0VbH2kQe9tNfL7cYw1Zr5mPa4sDu6iGo8EjKhT")
		String deviceSecret) {
}
