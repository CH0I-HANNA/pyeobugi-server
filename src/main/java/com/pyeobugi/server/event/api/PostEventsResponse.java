package com.pyeobugi.server.event.api;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** accepted + duplicated + rejected.size() = 요청 이벤트 수 */
@Schema(description = "배치 처리 결과. accepted + duplicated + rejected 개수 = 요청 이벤트 수",
		requiredProperties = { "accepted", "duplicated", "rejected" })
public record PostEventsResponse(
		@Schema(description = "새로 저장한 이벤트 수", minimum = "0", example = "1")
		int accepted,
		@Schema(description = "이미 저장된 eventId라 건너뛴 이벤트 수 (같은 배치 안의 중복 포함). 재전송 성공으로 보면 된다",
				minimum = "0", example = "1")
		int duplicated,
		@Schema(description = "검증에 걸려 저장하지 않은 이벤트. 같은 내용으로 다시 보내도 다시 거절된다")
		List<RejectedEvent> rejected) {
}
