package com.pyeobugi.server.report.service;

import com.pyeobugi.server.report.api.ReportSummaryResponse.Distribution;

/**
 * 자세 분포 (plan 8-6). 나쁜 자세(bad) = 살짝 숙임(mild) + 거북목(turtle).
 * 바른 자세 비율은 항상 postureKeepRate와 같게 고정하고, 나머지(100 − goodRate)를
 * mild : turtle 시간 비율대로 최대 잔여 방식(largest remainder)으로 나눠 합을 100으로 맞춘다.
 * mildDurationSec를 보내지 않는 구버전 클라이언트 구간은 mild = 0이라 전부 거북목이 된다.
 */
final class DistributionCalculator {

	private DistributionCalculator() {
	}

	/** 사용 시간이 0이면 null */
	static Distribution of(long usageSec, long badSec, long mildSec) {
		Integer goodRate = PostureKeepRate.of(usageSec, badSec);
		if (goodRate == null) {
			return null;
		}
		long turtleSec = badSec - mildSec;
		int restRate = 100 - goodRate;
		int mildRate = 0;
		int turtleRate = 0;
		if (badSec > 0) {
			mildRate = (int) (restRate * mildSec / badSec);
			turtleRate = (int) (restRate * turtleSec / badSec);
			if (mildRate + turtleRate < restRate) {
				// 남은 1%p는 나머지가 큰 쪽에 준다. 같으면 거북목
				long mildRemainder = restRate * mildSec % badSec;
				long turtleRemainder = restRate * turtleSec % badSec;
				if (mildRemainder > turtleRemainder) {
					mildRate++;
				} else {
					turtleRate++;
				}
			}
		}
		return new Distribution(usageSec - badSec, mildSec, turtleSec, goodRate, mildRate, turtleRate);
	}

}
