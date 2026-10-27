package com.pyeobugi.server.common;

import java.util.regex.Pattern;

/** UUID v4 형식 검사. 대소문자는 구분하지 않는다 (mock device.ts와 동일). */
public final class UuidV4 {

	private static final Pattern PATTERN = Pattern.compile(
			"^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$", Pattern.CASE_INSENSITIVE);

	private UuidV4() {
	}

	public static boolean matches(String value) {
		return value != null && PATTERN.matcher(value).matches();
	}

}
