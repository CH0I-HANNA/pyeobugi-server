package com.pyeobugi.server.report.api;

import java.util.List;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * GET /api/reports/summary 응답 (plan 7-3).
 * 기본 필드는 FE 10-27 수정안, yesterday·lastWeek·turtleNeckTimeline·distribution은 백엔드 확장 필드.
 * comment, distribution, postureKeepRate는 값이 없으면 null로 내려준다 (필드를 생략하지 않음).
 */
@Schema(description = "일간·주간 자세 리포트. 모든 날짜·시각은 KST(+09:00) 기준이며, 값이 없는 필드도 생략하지 않고 null로 내려준다.",
		requiredProperties = { "hasData", "generatedAt", "date", "day", "yesterday", "week", "lastWeek", "hourly",
				"turtleNeckTimeline", "distribution", "comment" })
public record ReportSummaryResponse(
		@Schema(description = "요청 날짜(`date`)에 사용 시간, 거북목 감지, 뽀모도로 완료 중 하나라도 있으면 true. "
				+ "false여도 `week`, `lastWeek`에는 같은 주 다른 날의 값이 있을 수 있다.", example = "true")
		boolean hasData,
		@Schema(description = "서버가 응답을 만든 시각 (KST, 초 단위)", example = "2026-10-04T14:05:00+09:00")
		String generatedAt,
		@Schema(description = "집계 기준 날짜 `D`. 요청의 `date`를 그대로 돌려주고, 생략했으면 서버 기준 KST 오늘",
				example = "2026-10-04")
		String date,
		@Schema(description = "`D` 하루(00:00~24:00). 오늘이면 현재까지")
		PeriodSummary day,
		@Schema(description = "`D − 1일` 하루. `D`가 월요일이면 지난주 일요일")
		PeriodSummary yesterday,
		@Schema(description = "`D`가 속한 주의 월요일 00:00 ~ `D` 24:00")
		PeriodSummary week,
		@Schema(description = "지난주 월요일 ~ 지난주 같은 요일(`D − 7일`). `week`와 같은 길이로 비교하기 위한 값")
		PeriodSummary lastWeek,
		@ArraySchema(arraySchema = @Schema(description = "`D`의 시간대별 유지율. 항상 24개, `hour` 0~23 오름차순"),
				minItems = 24, maxItems = 24)
		List<HourlyRate> hourly,
		@Schema(description = "`D`의 거북목 감지 시각과 감지가 몰린 시간대")
		TurtleNeckTimeline turtleNeckTimeline,
		@Schema(description = "`D`의 자세 분포. `D` 사용 시간이 0이면 null", nullable = true)
		Distribution distribution,
		@Schema(description = "`D` 유지율로 판정한 코멘트. `D` 사용 시간이 0이면 null", nullable = true)
		Comment comment) {

	@Schema(description = "기간별 집계",
			requiredProperties = { "postureKeepRate", "turtleNeckCount", "pomodoroCount", "usageSeconds" })
	public record PeriodSummary(
			@Schema(description = "바른 자세 유지율(%) = (사용 시간 − 나쁜 자세 시간) ÷ 사용 시간 × 100, 마지막에 한 번만 반올림. "
					+ "사용 시간이 0이면 null", minimum = "0", maximum = "100", nullable = true, example = "82")
			Integer postureKeepRate,
			@Schema(description = "거북목 감지 횟수 (`POSTURE_BAD` 개수, 고개 숙임 포함)", minimum = "0", example = "5")
			long turtleNeckCount,
			@Schema(description = "뽀모도로 완료 횟수", minimum = "0", example = "6")
			long pomodoroCount,
			@Schema(description = "사용 시간(초)", minimum = "0", example = "12600")
			long usageSeconds) {

		public static final PeriodSummary EMPTY = new PeriodSummary(null, 0, 0, 0);

	}

	@Schema(description = "시간대별 유지율. 시 경계를 걸친 사용 구간은 시작 시각(`startedAt`)의 시로 집계",
			requiredProperties = { "hour", "postureKeepRate" })
	public record HourlyRate(
			@Schema(description = "시 (KST)", minimum = "0", maximum = "23", example = "9")
			int hour,
			@Schema(description = "그 시간대의 바른 자세 유지율(%). 그 시간대 사용 시간이 0이면 null",
					minimum = "0", maximum = "100", nullable = true, example = "70")
			Integer postureKeepRate) {
	}

	/** occurredAt: 요청 날짜의 거북목 감지 시각(시간순), peakHours: 감지가 몰린 시간대(오름차순) */
	@Schema(description = "거북목 타임라인", requiredProperties = { "occurredAt", "peakHours" })
	public record TurtleNeckTimeline(
			@ArraySchema(arraySchema = @Schema(description = "`D`의 거북목 감지 시각, 시간순. 없으면 빈 배열"),
					schema = @Schema(example = "2026-10-04T09:22:40+09:00"))
			List<String> occurredAt,
			@ArraySchema(arraySchema = @Schema(description = "감지 횟수가 가장 많은 시간대(시). 최소 2회 이상일 때만, "
					+ "동점은 모두 포함, 오름차순. 없으면 빈 배열"),
					schema = @Schema(minimum = "0", maximum = "23", example = "14"))
			List<Integer> peakHours) {

		public static final TurtleNeckTimeline EMPTY = new TurtleNeckTimeline(List.of(), List.of());

	}

	/** 자세 분포. 데모는 2단계 판정이라 mild*는 0 (plan 8-6) */
	@Schema(description = "자세 분포. 비율 세 개의 합은 항상 100이고, `goodRate`는 `day.postureKeepRate`와 같다. "
			+ "현재 클라이언트는 살짝 숙임을 따로 판정하지 않아 `mildSeconds`, `mildRate`는 항상 0이다.",
			requiredProperties = { "goodSeconds", "mildSeconds", "turtleSeconds", "goodRate", "mildRate", "turtleRate" })
	public record Distribution(
			@Schema(description = "바른 자세 시간(초) = 사용 시간 − 나쁜 자세 시간", minimum = "0", example = "10332")
			long goodSeconds,
			@Schema(description = "살짝 숙임 시간(초). 3단계 판정 도입 전까지 항상 0", minimum = "0", example = "0")
			long mildSeconds,
			@Schema(description = "거북목 시간(초) = 나쁜 자세 시간 − 살짝 숙임 시간", minimum = "0", example = "2268")
			long turtleSeconds,
			@Schema(description = "바른 자세 비율(%)", minimum = "0", maximum = "100", example = "82")
			int goodRate,
			@Schema(description = "살짝 숙임 비율(%). 3단계 판정 도입 전까지 항상 0", minimum = "0", maximum = "100",
					example = "0")
			int mildRate,
			@Schema(description = "거북목 비율(%) = 100 − goodRate − mildRate", minimum = "0", maximum = "100",
					example = "18")
			int turtleRate) {
	}

	@Schema(description = "리포트 코멘트", requiredProperties = { "message", "mood" })
	public record Comment(
			@Schema(description = "화면에 보여 줄 문구", example = "오늘 잘했어요")
			String message,
			@Schema(description = "`day` 유지율 80% 이상 GOOD, 50% 이상 NORMAL, 50% 미만 BAD", example = "GOOD")
			Mood mood) {
	}

	public enum Mood {
		GOOD, NORMAL, BAD
	}

}
