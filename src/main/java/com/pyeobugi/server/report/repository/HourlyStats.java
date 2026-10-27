package com.pyeobugi.server.report.repository;

/** 시간대별 사용 시간 집계 결과. 기록이 있는 시간대만 나온다. */
public interface HourlyStats {

	Long getKstHour();

	Long getUsageSec();

	Long getBadSec();

}
