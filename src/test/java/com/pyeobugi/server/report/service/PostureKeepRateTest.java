package com.pyeobugi.server.report.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PostureKeepRateTest {

	@Test
	void zeroUsageIsNull() {
		assertThat(PostureKeepRate.of(0, 0)).isNull();
	}

	@Test
	void roundsHalfUpOnceAtTheEnd() {
		assertThat(PostureKeepRate.of(200, 43)).isEqualTo(79);   // 78.5
		assertThat(PostureKeepRate.of(200, 45)).isEqualTo(78);   // 77.5 → 78
		assertThat(PostureKeepRate.of(3, 1)).isEqualTo(67);      // 66.67
		assertThat(PostureKeepRate.of(3, 2)).isEqualTo(33);      // 33.33
	}

	@Test
	void boundaries() {
		assertThat(PostureKeepRate.of(60, 0)).isEqualTo(100);
		assertThat(PostureKeepRate.of(60, 60)).isZero();
	}

}
