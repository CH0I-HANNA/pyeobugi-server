package com.pyeobugi.server.report.service;

/** 정자세 유지율 산식 (plan 7-3, mock summary.ts와 동일) */
final class PostureKeepRate {

	private PostureKeepRate() {
	}

	/**
	 * (Σduration − Σbad) ÷ Σduration × 100을 마지막에 한 번만 반올림한다. 사용 시간이 0이면 null.
	 * 값이 항상 0 이상이라 Math.round(반 올림)는 JS Math.round, HALF_UP과 결과가 같다.
	 */
	static Integer of(long usageSec, long badSec) {
		if (usageSec == 0) {
			return null;
		}
		return (int) Math.round((usageSec - badSec) * 100.0 / usageSec);
	}

}
