package com.pyeobugi.server.event.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.pyeobugi.server.event.domain.EventEntity;

/** 저장은 insertIgnore만 쓴다. save()는 중복 시 배치 전체 실패나 덮어쓰기가 생긴다 (plan 6-4). */
public interface EventRepository extends JpaRepository<EventEntity, Long> {

	/** 반환값: 1 = 새로 저장, 0 = 중복 (device_id, event_id 유니크 키) */
	@Modifying
	@Query(value = """
			INSERT IGNORE INTO events
			  (device_id, event_id, session_id, type, occurred_at, kst_date, kst_hour,
			   duration_sec, bad_duration_sec, mild_duration_sec, payload, received_at)
			VALUES
			  (:deviceId, :eventId, :sessionId, :type, :occurredAt, :kstDate, :kstHour,
			   :durationSec, :badDurationSec, :mildDurationSec, :payload, :receivedAt)
			""", nativeQuery = true)
	int insertIgnore(@Param("deviceId") String deviceId,
			@Param("eventId") String eventId,
			@Param("sessionId") String sessionId,
			@Param("type") String type,
			@Param("occurredAt") LocalDateTime occurredAt,
			@Param("kstDate") LocalDate kstDate,
			@Param("kstHour") int kstHour,
			@Param("durationSec") Integer durationSec,
			@Param("badDurationSec") Integer badDurationSec,
			@Param("mildDurationSec") Integer mildDurationSec,
			@Param("payload") String payload,
			@Param("receivedAt") LocalDateTime receivedAt);

}
