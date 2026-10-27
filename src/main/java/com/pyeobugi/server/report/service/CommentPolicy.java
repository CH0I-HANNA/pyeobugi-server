package com.pyeobugi.server.report.service;

import com.pyeobugi.server.report.api.ReportSummaryResponse.Comment;
import com.pyeobugi.server.report.api.ReportSummaryResponse.Mood;

/** 코멘트 판정 (plan 8-6). 기준과 문구는 mock summary.ts와 같다. 문구는 기획 확정 후 바꾼다 (14장 #8). */
final class CommentPolicy {

	private static final int GOOD_FROM = 80;
	private static final int NORMAL_FROM = 50;

	private CommentPolicy() {
	}

	/** 요청 날짜(day)의 사용 시간이 0이면 null */
	static Comment of(long dayUsageSec, Integer dayKeepRate) {
		if (dayUsageSec == 0 || dayKeepRate == null) {
			return null;
		}
		if (dayKeepRate >= GOOD_FROM) {
			return new Comment("오늘 잘했어요", Mood.GOOD);
		}
		if (dayKeepRate >= NORMAL_FROM) {
			return new Comment("조금만 더 신경 써 볼까요?", Mood.NORMAL);
		}
		return new Comment("목을 쭉 펴 볼 시간이에요", Mood.BAD);
	}

}
