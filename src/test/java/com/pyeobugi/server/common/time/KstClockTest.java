package com.pyeobugi.server.common.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class KstClockTest {

	@Test
	void nowIsKstTruncatedToSeconds() {
		KstClock clock = new KstClock(Clock.fixed(Instant.parse("2026-10-04T05:05:00.700Z"), ZoneOffset.UTC));

		assertThat(clock.now()).isEqualTo(LocalDateTime.of(2026, 10, 4, 14, 5, 0));
	}

	@Test
	void todayFollowsKstEvenIfClockZoneIsUtc() {
		// UTC로는 10-04 15:00, KST로는 10-05 00:00
		KstClock clock = new KstClock(Clock.fixed(Instant.parse("2026-10-04T15:00:00Z"), ZoneOffset.UTC));

		assertThat(clock.today()).isEqualTo(LocalDate.of(2026, 10, 5));
	}

}
