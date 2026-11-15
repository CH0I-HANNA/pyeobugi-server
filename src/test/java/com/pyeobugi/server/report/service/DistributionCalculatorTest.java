package com.pyeobugi.server.report.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.pyeobugi.server.report.api.ReportSummaryResponse.Distribution;

class DistributionCalculatorTest {

	@Test
	void goodRateEqualsKeepRateAndRatesSumTo100() {
		Distribution d = DistributionCalculator.of(200, 43, 0);   // 유지율 78.5 → 79

		assertThat(d.goodSeconds()).isEqualTo(157);
		assertThat(d.turtleSeconds()).isEqualTo(43);
		assertThat(d.goodRate()).isEqualTo(PostureKeepRate.of(200, 43)).isEqualTo(79);
		assertThat(d.turtleRate()).isEqualTo(21);
		assertThat(d.goodRate() + d.turtleRate()).isEqualTo(100);
		assertThat(d.mildSeconds()).isZero();
		assertThat(d.mildRate()).isZero();
	}

	@Test
	void splitsBadIntoMildAndTurtle() {
		Distribution d = DistributionCalculator.of(200, 43, 13);   // 유지율 79, 나머지 21을 13:30으로

		assertThat(d.goodSeconds()).isEqualTo(157);
		assertThat(d.mildSeconds()).isEqualTo(13);
		assertThat(d.turtleSeconds()).isEqualTo(30);
		assertThat(d.goodRate()).isEqualTo(79);
		// 21 × 13/43 = 6.35, 21 × 30/43 = 14.65 → 내림 6 + 14 = 20, 남은 1은 나머지가 큰 거북목
		assertThat(d.mildRate()).isEqualTo(6);
		assertThat(d.turtleRate()).isEqualTo(15);
	}

	@Test
	void leftoverGoesToLargerRemainder() {
		Distribution d = DistributionCalculator.of(200, 43, 30);   // 21 × 30/43 = 14.65, 21 × 13/43 = 6.35

		assertThat(d.mildRate()).isEqualTo(15);
		assertThat(d.turtleRate()).isEqualTo(6);
	}

	@Test
	void ratesAlwaysSumTo100() {
		for (long usage = 1; usage <= 120; usage++) {
			for (long bad = 0; bad <= usage; bad++) {
				for (long mild = 0; mild <= bad; mild++) {
					Distribution d = DistributionCalculator.of(usage, bad, mild);
					assertThat(d.goodRate() + d.mildRate() + d.turtleRate()).isEqualTo(100);
					assertThat(d.goodRate()).isEqualTo(PostureKeepRate.of(usage, bad));
					assertThat(d.mildRate()).isNotNegative();
					assertThat(d.turtleRate()).isNotNegative();
				}
			}
		}
	}

	@Test
	void allMildMeansNoTurtle() {
		Distribution d = DistributionCalculator.of(100, 40, 40);

		assertThat(d.mildRate()).isEqualTo(40);
		assertThat(d.turtleRate()).isZero();
		assertThat(d.turtleSeconds()).isZero();
	}

	@Test
	void noUsageMeansNoDistribution() {
		assertThat(DistributionCalculator.of(0, 0, 0)).isNull();
	}

}
