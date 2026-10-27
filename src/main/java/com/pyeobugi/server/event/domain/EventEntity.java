package com.pyeobugi.server.event.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * events 테이블 매핑. 저장은 EventRepository의 네이티브 INSERT IGNORE로만 하고 save()는 쓰지 않는다 (plan 6-4, 8-3).
 */
@Entity
@Table(name = "events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String deviceId;
	private String eventId;
	private String sessionId;
	private String type;
	private LocalDateTime occurredAt;   // KST
	private LocalDate kstDate;
	private Integer kstHour;
	private Integer durationSec;
	private Integer badDurationSec;
	private Integer mildDurationSec;

	@JdbcTypeCode(SqlTypes.JSON)
	private String payload;

	private LocalDateTime receivedAt;   // KST

}
