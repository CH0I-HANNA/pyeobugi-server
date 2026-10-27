package com.pyeobugi.server.common.time;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * 현재 시각을 KST로 제공한다. 서버 코드는 LocalDateTime.now() 대신 이 클래스를 쓴다 (plan 10-2).
 */
@Component
@RequiredArgsConstructor
public class KstClock {

	private final Clock clock;

	/**
	 * KST 현재 시각 (초 단위). DB DATETIME은 소수 초를 반올림해 저장하므로 미리 버린다.
	 */
	public LocalDateTime now() {
		return LocalDateTime.ofInstant(clock.instant(), KstTimeUtils.KST).truncatedTo(ChronoUnit.SECONDS);
	}

	/** KST 오늘 날짜 */
	public LocalDate today() {
		return now().toLocalDate();
	}

}
