package com.pyeobugi.server.report.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 거북목 감지가 몰린 시간대 (plan 7-3 turtleNeckTimeline.peakHours).
 * 기준은 기획 확정 전 백엔드 제안값 (14장 #9): 가장 많은 시간대, 최소 2회, 동점은 모두 포함.
 */
final class PeakHours {

	static final int MIN_COUNT = 2;

	private PeakHours() {
	}

	static List<Integer> of(List<LocalDateTime> turtleNeckTimes) {
		Map<Integer, Long> countByHour = turtleNeckTimes.stream()
				.collect(Collectors.groupingBy(LocalDateTime::getHour, TreeMap::new, Collectors.counting()));
		long max = countByHour.values().stream().mapToLong(Long::longValue).max().orElse(0);
		if (max < MIN_COUNT) {
			return List.of();
		}
		return countByHour.entrySet().stream()
				.filter(e -> e.getValue() == max)
				.map(Map.Entry::getKey)
				.toList();
	}

}
