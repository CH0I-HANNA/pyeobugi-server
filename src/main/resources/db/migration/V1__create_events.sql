CREATE TABLE events (
  id                BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  device_id         CHAR(36)     NOT NULL,
  event_id          CHAR(36)     NOT NULL,
  session_id        CHAR(36)     NOT NULL,
  type              VARCHAR(32)  NOT NULL,
  occurred_at       DATETIME     NOT NULL COMMENT 'KST 기준 발생 시각',
  kst_date          DATE         NOT NULL COMMENT '집계 날짜: USAGE_SEGMENT는 startedAt, 나머지는 occurredAt',
  kst_hour          TINYINT      NOT NULL COMMENT '집계 시간대(0~23): kst_date와 같은 기준',
  duration_sec      INT          NULL     COMMENT 'USAGE_SEGMENT 전용',
  bad_duration_sec  INT          NULL     COMMENT 'USAGE_SEGMENT 전용',
  mild_duration_sec INT          NULL     COMMENT '3단계 자세 분포 확장용 (합의 전까지 항상 NULL)',
  payload           JSON         NOT NULL,
  received_at       DATETIME     NOT NULL COMMENT 'KST 기준 서버 수신 시각',
  UNIQUE KEY uk_device_event (device_id, event_id),
  KEY idx_agg (device_id, kst_date, type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
