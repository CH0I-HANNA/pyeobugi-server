package com.pyeobugi.server.report.repository;

/** 기간 지표 집계 결과 (SQL 컬럼 별칭과 getter 이름이 같아야 매핑된다) */
public interface PeriodStats {

	Long getUsageSec();

	Long getBadSec();

	/** 살짝 숙임 시간 합. 구버전 클라이언트 구간(NULL)은 0으로 더해진다 */
	Long getMildSec();

	Long getTurtleCount();

	Long getPomodoroCount();

}
