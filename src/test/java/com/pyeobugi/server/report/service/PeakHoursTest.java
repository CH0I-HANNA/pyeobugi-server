package com.pyeobugi.server.report.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class PeakHoursTest {

	@Test
	void hoursWithMostDetectionsInAscendingOrderIncludingTies() {
		assertThat(PeakHours.of(times("19:00", "14:05", "10:47", "14:30", "19:10"))).containsExactly(14, 19);
	}

	@Test
	void singleTopHour() {
		assertThat(PeakHours.of(times("09:00", "09:30", "09:59", "15:00", "15:01"))).containsExactly(9);
	}

	@Test
	void noPeakBelowMinimumCount() {
		assertThat(PeakHours.of(times("09:00", "15:00"))).isEmpty();
		assertThat(PeakHours.of(List.of())).isEmpty();
	}

	private static List<LocalDateTime> times(String... hhmm) {
		return Arrays.stream(hhmm).map(t -> LocalDateTime.parse("2026-10-08T" + t + ":00")).toList();
	}

}
