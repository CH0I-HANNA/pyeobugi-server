package com.pyeobugi.server.event.api;

import java.util.List;

/** accepted + duplicated + rejected.size() = 요청 이벤트 수 */
public record PostEventsResponse(int accepted, int duplicated, List<RejectedEvent> rejected) {
}
