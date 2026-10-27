package com.pyeobugi.server.report.repository;

/** 기간 지표 집계 결과 (SQL 컬럼 별칭과 getter 이름이 같아야 매핑된다) */
public interface PeriodStats {

	Long getUsageSec();

	Long getBadSec();

	Long getTurtleCount();

	Long getPomodoroCount();

}
