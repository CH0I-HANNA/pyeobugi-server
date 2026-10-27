package com.pyeobugi.server.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.pyeobugi.server.TestcontainersConfiguration;
import com.pyeobugi.server.event.domain.EventEntity;

import jakarta.persistence.EntityManager;

/** Flyway V1 스키마와 EventEntity 매핑이 맞는지 확인 (ddl-auto: none이라 실행 시 검증되지 않음) */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class EventEntityMappingTest {

	private static final String INSERT_SQL = """
			INSERT INTO events
			  (device_id, event_id, session_id, type, occurred_at, kst_date, kst_hour,
			   duration_sec, bad_duration_sec, mild_duration_sec, payload, received_at)
			VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
			""";

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	EntityManager entityManager;

	@Test
	void flywayCreatesEventsTableReadableByEntity() {
		jdbcTemplate.update(INSERT_SQL,
				"3f2b8c1e-6a4d-4e0b-9b7a-0c1d2e3f4a5b", "b1c2d3e4-0000-4000-8000-000000000003",
				"a9a9a9a9-0000-4000-8000-0000000000aa", "USAGE_SEGMENT",
				LocalDateTime.of(2026, 10, 4, 9, 23, 0), LocalDate.of(2026, 10, 4), 9,
				60, 12, null,
				"{\"startedAt\":\"2026-10-04T09:22:00+09:00\",\"endedAt\":\"2026-10-04T09:23:00+09:00\",\"durationSec\":60,\"badDurationSec\":12}",
				LocalDateTime.of(2026, 10, 4, 9, 23, 5));

		List<EventEntity> events = entityManager
				.createQuery("SELECT e FROM EventEntity e", EventEntity.class)
				.getResultList();

		assertThat(events).hasSize(1);
		EventEntity e = events.getFirst();
		assertThat(e.getId()).isNotNull();
		assertThat(e.getDeviceId()).isEqualTo("3f2b8c1e-6a4d-4e0b-9b7a-0c1d2e3f4a5b");
		assertThat(e.getEventId()).isEqualTo("b1c2d3e4-0000-4000-8000-000000000003");
		assertThat(e.getSessionId()).isEqualTo("a9a9a9a9-0000-4000-8000-0000000000aa");
		assertThat(e.getType()).isEqualTo("USAGE_SEGMENT");
		assertThat(e.getOccurredAt()).isEqualTo(LocalDateTime.of(2026, 10, 4, 9, 23, 0));
		assertThat(e.getKstDate()).isEqualTo(LocalDate.of(2026, 10, 4));
		assertThat(e.getKstHour()).isEqualTo(9);
		assertThat(e.getDurationSec()).isEqualTo(60);
		assertThat(e.getBadDurationSec()).isEqualTo(12);
		assertThat(e.getMildDurationSec()).isNull();
		assertThat(e.getPayload()).contains("\"durationSec\": 60").contains("\"badDurationSec\": 12");
		assertThat(e.getReceivedAt()).isEqualTo(LocalDateTime.of(2026, 10, 4, 9, 23, 5));
	}

	@Test
	void sameDeviceAndEventIdIsRejectedByUniqueKey() {
		Object[] row = {
				"3f2b8c1e-6a4d-4e0b-9b7a-0c1d2e3f4a5b", "b1c2d3e4-0000-4000-8000-000000000001",
				"a9a9a9a9-0000-4000-8000-0000000000aa", "SESSION_START",
				LocalDateTime.of(2026, 10, 4, 9, 0, 0), LocalDate.of(2026, 10, 4), 9,
				null, null, null, "{}", LocalDateTime.of(2026, 10, 4, 9, 0, 5)};
		jdbcTemplate.update(INSERT_SQL, row);

		assertThatThrownBy(() -> jdbcTemplate.update(INSERT_SQL, row))
				.isInstanceOf(DuplicateKeyException.class);
	}

}
