package com.pyeobugi.server.common.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class KstTimeUtilsTest {

	@Test
	void parseDateTimeAcceptsKstOffsetWithSeconds() {
		assertThat(KstTimeUtils.parseDateTime("2026-10-04T09:23:00+09:00"))
				.contains(LocalDateTime.of(2026, 10, 4, 9, 23, 0));
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = {
			"2026-10-04T00:23:00Z",            // Z
			"2026-10-04T00:23:00+00:00",       // 다른 오프셋
			"2026-10-04T09:23:00.000+09:00",   // 밀리초
			"2026-10-04T09:23:00",             // 오프셋 없음
			"2026-10-04T09:23+09:00",          // 초 없음
			"2026-10-04 09:23:00+09:00",       // T 없음
			"2026-10-04T09:23:00+0900",        // 콜론 없는 오프셋
			"2026-02-30T09:23:00+09:00",       // 없는 날짜
			"2026-10-04T24:00:00+09:00",       // 없는 시각
			""
	})
	void parseDateTimeRejectsOtherFormats(String value) {
		assertThat(KstTimeUtils.parseDateTime(value)).isEmpty();
	}

	@Test
	void parseDateAcceptsValidDates() {
		assertThat(KstTimeUtils.parseDate("2026-10-04")).contains(LocalDate.of(2026, 10, 4));
		assertThat(KstTimeUtils.parseDate("2028-02-29")).contains(LocalDate.of(2028, 2, 29));
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = {"2026-1-5", "2026-02-30", "2026-02-29", "2026-13-01", "abc", "2026-10-04T00:00", ""})
	void parseDateRejectsInvalidDates(String value) {
		assertThat(KstTimeUtils.parseDate(value)).isEmpty();
	}

	@Test
	void formatDateTimeAlwaysIncludesSecondsAndKstOffset() {
		assertThat(KstTimeUtils.formatDateTime(LocalDateTime.of(2026, 10, 4, 14, 5)))
				.isEqualTo("2026-10-04T14:05:00+09:00");
	}

	@Test
	void weekStartIsMondayOfSameWeek() {
		LocalDate monday = LocalDate.of(2026, 10, 5);
		assertThat(KstTimeUtils.weekStart(LocalDate.of(2026, 10, 5))).isEqualTo(monday);   // 월
		assertThat(KstTimeUtils.weekStart(LocalDate.of(2026, 10, 7))).isEqualTo(monday);   // 수
		assertThat(KstTimeUtils.weekStart(LocalDate.of(2026, 10, 11))).isEqualTo(monday);  // 일
		assertThat(KstTimeUtils.weekStart(LocalDate.of(2026, 10, 12))).isEqualTo(LocalDate.of(2026, 10, 12));
	}

}
