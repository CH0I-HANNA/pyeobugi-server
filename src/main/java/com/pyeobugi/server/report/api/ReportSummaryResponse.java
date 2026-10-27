package com.pyeobugi.server.report.api;

import java.util.List;

/**
 * GET /api/reports/summary 응답 (plan 7-3, FE 10-27 수정안).
 * comment, postureKeepRate는 값이 없으면 null로 내려준다 (필드를 생략하지 않음).
 */
public record ReportSummaryResponse(
		boolean hasData,
		String generatedAt,
		String date,
		PeriodSummary day,
		PeriodSummary week,
		List<HourlyRate> hourly,
		Comment comment) {

	public record PeriodSummary(Integer postureKeepRate, long turtleNeckCount, long pomodoroCount, long usageSeconds) {

		public static final PeriodSummary EMPTY = new PeriodSummary(null, 0, 0, 0);

	}

	public record HourlyRate(int hour, Integer postureKeepRate) {
	}

	public record Comment(String message, Mood mood) {
	}

	public enum Mood {
		GOOD, NORMAL, BAD
	}

}
