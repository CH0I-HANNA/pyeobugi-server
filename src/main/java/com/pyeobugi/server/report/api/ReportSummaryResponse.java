package com.pyeobugi.server.report.api;

import java.util.List;

/**
 * GET /api/reports/summary 응답 (plan 7-3).
 * 기본 필드는 FE 10-27 수정안, yesterday·lastWeek·turtleNeckTimeline·distribution은 백엔드 확장 필드.
 * comment, distribution, postureKeepRate는 값이 없으면 null로 내려준다 (필드를 생략하지 않음).
 */
public record ReportSummaryResponse(
		boolean hasData,
		String generatedAt,
		String date,
		PeriodSummary day,
		PeriodSummary yesterday,
		PeriodSummary week,
		PeriodSummary lastWeek,
		List<HourlyRate> hourly,
		TurtleNeckTimeline turtleNeckTimeline,
		Distribution distribution,
		Comment comment) {

	public record PeriodSummary(Integer postureKeepRate, long turtleNeckCount, long pomodoroCount, long usageSeconds) {

		public static final PeriodSummary EMPTY = new PeriodSummary(null, 0, 0, 0);

	}

	public record HourlyRate(int hour, Integer postureKeepRate) {
	}

	/** occurredAt: 요청 날짜의 거북목 감지 시각(시간순), peakHours: 감지가 몰린 시간대(오름차순) */
	public record TurtleNeckTimeline(List<String> occurredAt, List<Integer> peakHours) {

		public static final TurtleNeckTimeline EMPTY = new TurtleNeckTimeline(List.of(), List.of());

	}

	/** 자세 분포. 데모는 2단계 판정이라 mild*는 null (plan 8-6) */
	public record Distribution(
			long goodSeconds, Long mildSeconds, long turtleSeconds,
			int goodRate, Integer mildRate, int turtleRate) {
	}

	public record Comment(String message, Mood mood) {
	}

	public enum Mood {
		GOOD, NORMAL, BAD
	}

}
