package com.pyeobugi.server.report.service;

import com.pyeobugi.server.report.api.ReportSummaryResponse.Distribution;

/**
 * 자세 분포 (plan 8-6). 바른 자세 비율은 항상 postureKeepRate와 같게 고정하고, 나머지를 거북목에 준다.
 * 2단계라 합이 항상 100이다. 3단계(mild)가 생기면 나머지를 최대 잔여 방식으로 나눈다 (plan 13장 10단계).
 */
final class DistributionCalculator {

	private DistributionCalculator() {
	}

	/** 사용 시간이 0이면 null */
	static Distribution of(long usageSec, long badSec) {
		Integer goodRate = PostureKeepRate.of(usageSec, badSec);
		if (goodRate == null) {
			return null;
		}
		return new Distribution(usageSec - badSec, null, badSec, goodRate, null, 100 - goodRate);
	}

}
