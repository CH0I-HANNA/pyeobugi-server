package com.pyeobugi.server.report.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.pyeobugi.server.event.domain.EventEntity;

/**
 * 리포트 집계 쿼리 (plan 8-5). 집계 날짜·시간대는 저장할 때 계산해 둔 kst_date, kst_hour를 쓴다.
 * MySQL SUM()은 DECIMAL을 돌려주므로 CAST(... AS SIGNED)로 정수로 바꾼다.
 */
public interface ReportQueryRepository extends Repository<EventEntity, Long> {

	/** 기간 지표: day(from = to = date), week(from = 그 주 월요일, to = date) */
	@Query(value = """
			SELECT
			  CAST(COALESCE(SUM(duration_sec), 0)                AS SIGNED) AS usageSec,
			  CAST(COALESCE(SUM(bad_duration_sec), 0)            AS SIGNED) AS badSec,
			  CAST(COALESCE(SUM(type = 'POSTURE_BAD'), 0)        AS SIGNED) AS turtleCount,
			  CAST(COALESCE(SUM(type = 'POMODORO_COMPLETED'), 0) AS SIGNED) AS pomodoroCount
			FROM events
			WHERE device_id = :deviceId
			  AND kst_date BETWEEN :from AND :to
			  AND type IN ('USAGE_SEGMENT', 'POSTURE_BAD', 'POMODORO_COMPLETED')
			""", nativeQuery = true)
	PeriodStats findPeriodStats(@Param("deviceId") String deviceId,
			@Param("from") LocalDate from,
			@Param("to") LocalDate to);

	/** 시간대별 사용 시간. 24칸 채우기는 ReportService에서 한다 */
	@Query(value = """
			SELECT CAST(kst_hour AS SIGNED)              AS kstHour,
			       CAST(SUM(duration_sec) AS SIGNED)     AS usageSec,
			       CAST(SUM(bad_duration_sec) AS SIGNED) AS badSec
			FROM events
			WHERE device_id = :deviceId AND kst_date = :date
			  AND type = 'USAGE_SEGMENT' AND duration_sec > 0
			GROUP BY kst_hour
			ORDER BY kst_hour
			""", nativeQuery = true)
	List<HourlyStats> findHourlyStats(@Param("deviceId") String deviceId,
			@Param("date") LocalDate date);

}
