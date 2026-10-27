package com.pyeobugi.server.event.domain;

import java.util.Arrays;
import java.util.Optional;

/** 이벤트 종류 (plan 7-2). 타입별 payload 규칙은 PayloadRules에 있다. */
public enum EventType {

	SESSION_START,
	SESSION_END,
	USAGE_SEGMENT,
	POSTURE_BAD,
	POMODORO_COMPLETED,
	CALIBRATION_COMPLETED,
	BUBBLE_SHOWN,
	SETTINGS_CHANGED;

	public static Optional<EventType> from(String name) {
		return Arrays.stream(values()).filter(t -> t.name().equals(name)).findFirst();
	}

}
