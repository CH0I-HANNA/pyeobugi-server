package com.pyeobugi.server.report.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.pyeobugi.server.report.api.ReportSummaryResponse.Distribution;

class DistributionCalculatorTest {

	@Test
	void goodRateEqualsKeepRateAndRatesSumTo100() {
		Distribution d = DistributionCalculator.of(200, 43);   // 유지율 78.5 → 79

		assertThat(d.goodSeconds()).isEqualTo(157);
		assertThat(d.turtleSeconds()).isEqualTo(43);
		assertThat(d.goodRate()).isEqualTo(PostureKeepRate.of(200, 43)).isEqualTo(79);
		assertThat(d.turtleRate()).isEqualTo(21);
		assertThat(d.goodRate() + d.turtleRate()).isEqualTo(100);
		assertThat(d.mildSeconds()).isNull();
		assertThat(d.mildRate()).isNull();
	}

	@Test
	void noUsageMeansNoDistribution() {
		assertThat(DistributionCalculator.of(0, 0)).isNull();
	}

}
