package com.pyeobugi.server.report.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pyeobugi.server.common.error.ApiException;
import com.pyeobugi.server.common.error.ErrorCode;
import com.pyeobugi.server.common.time.KstClock;
import com.pyeobugi.server.common.time.KstTimeUtils;
import com.pyeobugi.server.report.api.ReportSummaryResponse;
import com.pyeobugi.server.report.api.ReportSummaryResponse.HourlyRate;
import com.pyeobugi.server.report.api.ReportSummaryResponse.PeriodSummary;
import com.pyeobugi.server.report.repository.HourlyStats;
import com.pyeobugi.server.report.repository.PeriodStats;
import com.pyeobugi.server.report.repository.ReportQueryRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReportService {

	private static final int HOURS = 24;

	private final ReportQueryRepository reportQueryRepository;
	private final KstClock clock;

	/**
	 * date(KST, YYYY-MM-DD)의 리포트. date가 null이면 오늘 (plan 7-3).
	 * 세 쿼리가 같은 시점의 데이터를 보도록 읽기 전용 트랜잭션 하나에서 실행한다.
	 */
	@Transactional(readOnly = true)
	public ReportSummaryResponse summarize(String deviceId, String dateParam) {
		LocalDateTime now = clock.now();
		LocalDate date = dateParam == null ? now.toLocalDate() : parseDate(dateParam);
		String generatedAt = KstTimeUtils.formatDateTime(now);

		// 미래 날짜는 400 대신 빈 응답 (14장 #20 제안: 클라이언트 시계가 조금 빠른 자정 직후 대비)
		if (date.isAfter(now.toLocalDate())) {
			return new ReportSummaryResponse(false, generatedAt, date.toString(),
					PeriodSummary.EMPTY, PeriodSummary.EMPTY, emptyHourly(), null);
		}

		PeriodStats dayStats = reportQueryRepository.findPeriodStats(deviceId, date, date);
		PeriodStats weekStats = reportQueryRepository.findPeriodStats(deviceId, KstTimeUtils.weekStart(date), date);
		PeriodSummary day = toSummary(dayStats);

		return new ReportSummaryResponse(
				hasData(day),
				generatedAt,
				date.toString(),
				day,
				toSummary(weekStats),
				toHourly(reportQueryRepository.findHourlyStats(deviceId, date)),
				CommentPolicy.of(day.usageSeconds(), day.postureKeepRate()));
	}

	private static LocalDate parseDate(String value) {
		return KstTimeUtils.parseDate(value).orElseThrow(() ->
				new ApiException(ErrorCode.INVALID_REQUEST, "date는 YYYY-MM-DD 형식의 존재하는 날짜여야 합니다."));
	}

	/** 요청 날짜에 사용 기록, 거북목, 뽀모도로 중 하나라도 있으면 true (FE 10-27 확정) */
	private static boolean hasData(PeriodSummary day) {
		return day.usageSeconds() > 0 || day.turtleNeckCount() > 0 || day.pomodoroCount() > 0;
	}

	private static PeriodSummary toSummary(PeriodStats s) {
		return new PeriodSummary(
				PostureKeepRate.of(s.getUsageSec(), s.getBadSec()),
				s.getTurtleCount(),
				s.getPomodoroCount(),
				s.getUsageSec());
	}

	/** 항상 0~23시 24개. 기록이 없는 시간대는 postureKeepRate: null */
	private static List<HourlyRate> toHourly(List<HourlyStats> stats) {
		Integer[] rates = new Integer[HOURS];
		for (HourlyStats s : stats) {
			rates[s.getKstHour().intValue()] = PostureKeepRate.of(s.getUsageSec(), s.getBadSec());
		}
		List<HourlyRate> hourly = new ArrayList<>(HOURS);
		for (int hour = 0; hour < HOURS; hour++) {
			hourly.add(new HourlyRate(hour, rates[hour]));
		}
		return hourly;
	}

	private static List<HourlyRate> emptyHourly() {
		return toHourly(List.of());
	}

}
