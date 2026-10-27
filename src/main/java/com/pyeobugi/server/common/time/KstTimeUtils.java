package com.pyeobugi.server.common.time;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * KST 시각·날짜 변환은 이 클래스에서만 한다 (plan 6-3).
 */
public final class KstTimeUtils {

	public static final ZoneId KST = ZoneId.of("Asia/Seoul");

	/** 요청 시각 형식: +09:00 오프셋, 초 단위, 밀리초 없음 */
	private static final Pattern DATE_TIME_PATTERN =
			Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\+09:00$");

	private static final Pattern DATE_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

	private static final DateTimeFormatter DATE_TIME_FORMATTER =
			DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ssXXX").withResolverStyle(ResolverStyle.STRICT);

	private static final DateTimeFormatter DATE_FORMATTER =
			DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);

	private KstTimeUtils() {
	}

	/**
	 * 요청의 시각 문자열을 KST LocalDateTime으로 바꾼다.
	 * 형식이 다르거나(Z, 다른 오프셋, 밀리초, 오프셋 없음) 존재하지 않는 시각이면 empty.
	 */
	public static Optional<LocalDateTime> parseDateTime(String value) {
		if (value == null || !DATE_TIME_PATTERN.matcher(value).matches()) {
			return Optional.empty();
		}
		try {
			return Optional.of(OffsetDateTime.parse(value, DATE_TIME_FORMATTER).toLocalDateTime());
		} catch (DateTimeParseException e) {
			return Optional.empty();
		}
	}

	/** 리포트 date 쿼리(YYYY-MM-DD)를 파싱한다. 형식 오류, 존재하지 않는 날짜면 empty. */
	public static Optional<LocalDate> parseDate(String value) {
		if (value == null || !DATE_PATTERN.matcher(value).matches()) {
			return Optional.empty();
		}
		try {
			return Optional.of(LocalDate.parse(value, DATE_FORMATTER));
		} catch (DateTimeParseException e) {
			return Optional.empty();
		}
	}

	/** KST LocalDateTime을 응답 형식(2026-10-04T14:05:00+09:00)으로 바꾼다. */
	public static String formatDateTime(LocalDateTime kstDateTime) {
		return kstDateTime.atZone(KST).format(DATE_TIME_FORMATTER);
	}

	/** date가 속한 주의 월요일 */
	public static LocalDate weekStart(LocalDate date) {
		return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
	}

}
