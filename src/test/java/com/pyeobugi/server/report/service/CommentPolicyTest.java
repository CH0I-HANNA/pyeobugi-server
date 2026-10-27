package com.pyeobugi.server.report.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.pyeobugi.server.report.api.ReportSummaryResponse.Mood;

class CommentPolicyTest {

	@Test
	void moodThresholdsAreEightyAndFifty() {
		assertThat(CommentPolicy.of(60, 100).mood()).isEqualTo(Mood.GOOD);
		assertThat(CommentPolicy.of(60, 80).mood()).isEqualTo(Mood.GOOD);
		assertThat(CommentPolicy.of(60, 79).mood()).isEqualTo(Mood.NORMAL);
		assertThat(CommentPolicy.of(60, 50).mood()).isEqualTo(Mood.NORMAL);
		assertThat(CommentPolicy.of(60, 49).mood()).isEqualTo(Mood.BAD);
		assertThat(CommentPolicy.of(60, 0).mood()).isEqualTo(Mood.BAD);
	}

	@Test
	void messagesMatchMock() {
		assertThat(CommentPolicy.of(60, 80).message()).isEqualTo("오늘 잘했어요");
		assertThat(CommentPolicy.of(60, 79).message()).isEqualTo("조금만 더 신경 써 볼까요?");
		assertThat(CommentPolicy.of(60, 49).message()).isEqualTo("목을 쭉 펴 볼 시간이에요");
	}

	@Test
	void noUsageMeansNoComment() {
		assertThat(CommentPolicy.of(0, null)).isNull();
	}

}
