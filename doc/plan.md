# 펴부기 백엔드 개발 계획

| 항목 | 내용 |
| --- | --- |
| 작성일 | 2026-10-26 (v3 갱신 2026-10-27) |
| 담당 | 백엔드 |
| 상태 | 초안 v3 (14장의 확인 사항 확정 후 갱신) |
| 기준 문서 | 노션 API 명세(FE 초안 + 26-10-27 수정사항), 기획서(서버·리포트), AI 파트 레포 `docs/handoff-backend.md` |

**v3 변경 사항** (FE 26-10-27 수정사항 반영)

| 항목 | v2 | v3 |
| --- | --- | --- |
| 리포트 조회 | 파라미터 없음, 항상 오늘 | **`?date=YYYY-MM-DD`** (선택, 생략 시 오늘). 형식 오류·없는 날짜는 `400 INVALID_REQUEST` |
| 리포트 응답 | `today` | **`day`로 이름 변경** (요청한 날짜 하루치), 응답에 **`date` echo** 추가 |
| `week` | 이번 주 월요일 ~ 현재 | **요청 날짜가 속한 주의 월요일 ~ 요청 날짜** |
| `hourly` | 기록 있는 시간대만, `usageSeconds` 포함 | **항상 24개** (0~23시), `{ hour, postureKeepRate }`, 기록 없으면 `null` |
| `hasData` | 전체 기간 기준 | **요청 날짜(`day`) 기준**. 별도 `EXISTS` 쿼리 없이 `day` 집계값으로 판정 |
| 시각 형식 | 오프셋 포함 ISO 8601 | **`+09:00` 오프셋, 초 단위만 허용** (`Z`, 다른 오프셋, 밀리초는 `INVALID_PAYLOAD`) |
| 거부 사유 우선순위 | 검사 순서대로 | **`FORBIDDEN_FIELD` 우선** |
| 에러 body | `{ code, message, traceId }` | `{ code, message?, traceId?, retryAfter? }` (FE는 `code`만 필수) |
| 기존 확장 필드 | `yesterday`, `lastWeek`, `turtleNeckTimeline`, `distribution` | 요청 날짜 기준으로 재정의, FE 수요 확인 전까지 보류 (14장 #19) |

**v2 변경 사항**

| 항목 | v1 | v2 |
| --- | --- | --- |
| Spring Boot | 3.x | **4.0.x** (3.5 오픈소스 지원이 2026-06-30 종료) |
| DB 접근 | JdbcTemplate | **Spring Data JPA + 네이티브 쿼리** |
| 멱등 저장 | 다중 행 `INSERT IGNORE` 한 문장, 영향받은 행 수로 계산 | **이벤트마다 `INSERT IGNORE`, 반환값 1/0 합산** |
| 시간대 고정 | JVM, DB 연결 | JVM, DB 연결, Hibernate `jdbc.time_zone` |
| 추가 | — | 4-2 의존성 목록, 4-3 프로젝트 생성 설정, 6-4 JPA 매핑, 8-5 프로젝션 |

---

## 목차

1. [개요](#1-개요)
2. [범위](#2-범위)
3. [시스템 구조](#3-시스템-구조)
4. [기술 스택](#4-기술-스택)
5. [프로젝트 구조](#5-프로젝트-구조)
6. [데이터베이스 설계](#6-데이터베이스-설계)
7. [API 설계](#7-api-설계)
8. [핵심 구현 설계](#8-핵심-구현-설계)
9. [AI 파트(클라이언트)와의 연동](#9-ai-파트클라이언트와의-연동)
10. [테스트 전략](#10-테스트-전략)
11. [배포와 운영](#11-배포와-운영)
12. [운영 SQL](#12-운영-sql)
13. [개발 단계](#13-개발-단계)
14. [확인이 필요한 사항](#14-확인이-필요한-사항)
15. [리스크](#15-리스크)

---

## 1. 개요

### 1-1. 목적

펴부기 데스크톱 앱(Electron)이 보내는 익명 이벤트 로그를 **수집(collect)** 하고, 사용자별 리포트를 **집계(aggregate)** 해서 **조회(query)** 할 수 있게 하는 서버를 만든다. 기획자의 운영 지표 확인은 백오피스(Back Office) 화면 없이 **MySQL 직접 조회**로 대신한다.

### 1-2. 핵심 원칙

> **영상, 얼굴, 기준값은 기기에 남기고, 서버로는 이벤트 로그만 받는다.**

| 원칙 | 서버에서 지키는 방법 |
| --- | --- |
| 개인 식별 정보 없음 | 로그인 없음, 익명 기기 ID(UUID v4)로만 식별 |
| 자세 수치 미수집 | 화이트리스트(whitelist) 검증으로 pitch, 얼굴 크기, 기준값, 슬라이더 값 등 정의되지 않은 필드를 거절 |
| 원본 IP 미저장 | IP는 Rate Limit 용도로 메모리에서 해시값만 사용, DB에 저장하지 않음 |
| 요청 본문 미기록 | 서버 로그에 요청 본문(request body)을 남기지 않음 |
| 통신 암호화 | 배포 환경은 HTTPS만 허용 |

---

## 2. 범위

### 2-1. 처리 위치 구분

| 기능 | 처리 위치 | 서버 역할 |
| --- | --- | --- |
| 진입 분기, 카메라 권한, 얼굴 검출, 기준값 저장 | 로컬 | 없음 |
| 캘리브레이션 완료 | 로컬 + 서버 | `CALIBRATION_COMPLETED` 수신 (재설정 시 `isRecalibration: true`) |
| 설정 슬라이더, 토글 | 로컬 | 토글 on/off만 `SETTINGS_CHANGED`로 수신 |
| 자세 판정, 사용 시간, 뽀모도로 | 로컬 + 서버 | 이벤트 수신 후 집계 |
| 메인 리포트 (날짜별 기록) | 서버 | `GET /api/reports/summary?date=` |
| 운영 지표 조회 | 서버(DB) | 읽기 전용 계정 + 운영 SQL 제공 |

### 2-2. 산출물

| 산출물 | 형태 |
| --- | --- |
| Spring Boot 서버 | 수집 API, 조회 API, 집계 로직 |
| MySQL 스키마 | Flyway 마이그레이션 스크립트 |
| 응답 규격 | springdoc-openapi 자동 생성 문서 (Swagger UI) |
| 운영 예시 SQL | 12장의 SQL + 접속 가이드 문서 |

### 2-3. 범위 밖 (향후 확장)

로그인과 계정 통합, 관리자 API와 백오피스 화면, AI 대시보드용 인사이트 집계, 모바일 연동.

---

## 3. 시스템 구조

```
┌──────────────── 사용자 PC: 펴부기 앱 (AI 파트 레포, Electron) ────────────────┐
│                                                                                │
│  웹캠 → MediaPipe 얼굴 검출 → pitch 근사 → 나쁜 자세 판정 (전부 로컬 처리)     │
│                                       │                                        │
│                                       ▼                                        │
│              이벤트 생성 (USAGE_SEGMENT, POSTURE_BAD, ...)                     │
│                                       │                                        │
│                                       ▼                                        │
│              지속성 큐(persistent queue) → 10초마다 또는 20개마다 배치 전송    │
└───────────────────────────────────────┼────────────────────────────────────────┘
                                        │  HTTPS + JSON
                                        │  헤더: X-Device-Id
                                        │  POST /api/events
                                        │  GET  /api/reports/summary
                                        ▼
┌──────────────────────────── 서버: Spring Boot ─────────────────────────────────┐
│                                                                                │
│  필터(Filter): traceId → 기기 ID 검증 → Rate Limit → 본문 크기 제한            │
│                                       │                                        │
│              ┌────────────────────────┴────────────────────────┐               │
│              ▼                                                 ▼               │
│     이벤트 수집 (event)                              리포트 조회 (report)      │
│     구조 검증 → 이벤트별 검증 → INSERT IGNORE        집계 쿼리 → 코멘트 판정   │
│              │                                                 ▲               │
│              └──────────────────────┬──────────────────────────┘               │
│                                     ▼                                          │
│                              MySQL (events 테이블)                             │
└────────────────────────────────────────────────────────────────────────────────┘
                                     ▲
                                     │ SSH 터널 + 읽기 전용 계정
                               기획자 (운영 SQL)
```

---

## 4. 기술 스택

### 4-1. 스택 요약

| 영역 | 선택 | 이유 |
| --- | --- | --- |
| 언어 / 프레임워크 | Java 21, Spring Boot 4.0.x | 3.x의 마지막 버전(3.5) 오픈소스 지원이 2026-06-30에 종료. SNAPSHOT·M·RC가 아닌 최신 안정 버전 사용 |
| DB | MySQL 8.x | 기획서 지정. 윈도우 함수(window function) 지원으로 운영 SQL 작성이 쉬움 |
| DB 접근 | Spring Data JPA + 네이티브 쿼리 | 엔티티·리포지토리 구조는 JPA로 잡고, 멱등 삽입(`INSERT IGNORE`)과 조건부 집계는 네이티브 SQL로 작성. 향후 로그인·계정 테이블 확장에도 대비 |
| 스키마 관리 | Flyway | 로컬, 테스트, 배포 DB의 스키마 일치. JPA `ddl-auto`는 사용하지 않음 |
| Rate Limit | Bucket4j + Caffeine 캐시 | 서버 1대 기준 인메모리로 충분, 기기별 버킷 자동 만료 |
| API 문서 | springdoc-openapi 3.x | Boot 4.x는 springdoc 3.x 계열 사용. 서버 코드에서 OpenAPI 문서 자동 생성 |
| 테스트 | JUnit 5, MockMvc, Testcontainers(MySQL) | MySQL 고유 문법을 실제 DB로 검증 |
| 빌드 | Gradle (Groovy) | — |
| IDE | IntelliJ IDEA | 학생 교육용 라이선스로 Ultimate 사용 가능 |

### 4-2. 의존성

**Spring Initializr에서 선택**

| 구분 | Initializr 이름 | 용도 |
| --- | --- | --- |
| 필수 | Spring Web | REST API, 필터, 컨트롤러 |
| 필수 | Validation | Bean Validation |
| 필수 | Spring Data JPA | 엔티티, 리포지토리, 네이티브 쿼리 (JDBC 포함) |
| 필수 | MySQL Driver | MySQL 연결 |
| 필수 | Flyway Migration | 스키마 관리. `flyway-mysql` 모듈이 같이 추가됐는지 확인하고, 없으면 직접 추가 |
| 필수 | Testcontainers | 테스트용 MySQL 컨테이너 |
| 선택 | Lombok | 반복 코드 감소 |
| 선택 | Spring Boot DevTools | 코드 수정 시 자동 재시작 |
| 선택 | Docker Compose Support | 실행 시 로컬 MySQL 컨테이너 자동 실행 |
| 선택 | Spring Boot Actuator | `/actuator/health`로 배포 후 상태 확인 |

**선택하지 않는 것**

| Initializr 이름 | 이유 |
| --- | --- |
| Spring Security | 모든 API에 기본 로그인이 걸려 기기 ID 헤더 방식의 요청이 401로 막힘 |
| H2 Database | MySQL 문법(`INSERT IGNORE` 등) 검증이 부정확해짐 |

**직접 추가 (`build.gradle`)**

```groovy
dependencies {
    // ... Initializr가 만든 의존성들 ...

    // API 문서 (Swagger UI) — Boot 버전과 호환되는 3.x 버전 사용
    implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3'

    // Rate Limit
    implementation 'com.bucket4j:bucket4j_jdk17-core:8.x.x'

    // 기기별 Rate Limit 버킷 캐시 (버전은 Spring Boot가 관리)
    implementation 'com.github.ben-manes.caffeine:caffeine'
}
```

- `x`는 Maven Central에서 최신 버전을 확인해 채운다. Bucket4j는 버전에 따라 아티팩트 이름이 바뀐 적이 있으므로 검색 결과의 아티팩트명을 그대로 쓴다.
- springdoc은 Boot의 마이너 버전을 늦게 따라가는 경우가 있다. Initializr 기본값이 4.1.x 이상이면 **springdoc 지원이 확인된 Boot 버전**으로 맞춘다. 현재 Boot 4.0.9 + springdoc 3.0.3 (3.1.x부터는 Boot 4.1 기준).
- Boot 4부터는 Jackson 3가 기본이라 `JsonNode`의 import 경로가 예전 자료와 다를 수 있다. 예제 코드를 옮길 때 IDE 자동 import로 프로젝트에 실제로 있는 패키지를 선택한다.

### 4-3. 프로젝트 생성 설정

| 항목 | 값 | 비고 |
| --- | --- | --- |
| Project | Gradle - Groovy | — |
| Language | Java | — |
| Spring Boot | 4.0.x 최신 안정 버전 | 4-2 springdoc 호환성 참고 |
| Group | `com.pyeobugi` | — |
| Artifact | `pyeobugi-server` | jar 이름, 폴더 이름 |
| Name | `pyeobugi-server` | 메인 클래스 `PyeobugiServerApplication` 생성 |
| Description | 펴부기 이벤트 수집·리포트 API 서버 | — |
| Package name | `com.pyeobugi.server` | **직접 수정.** 자동 생성값은 하이픈 때문에 `com.pyeobugi.pyeobugiserver`가 됨 |
| Packaging | Jar | — |
| Java | 21 | — |

서비스 이름이 바뀌면 `settings.gradle`의 `rootProject.name`, `application.yml`의 `spring.application.name`, 패키지 이름(IntelliJ **Refactor → Rename**)을 함께 고친다.

---

## 5. 프로젝트 구조

```
pyeobugi-server/
├── build.gradle
└── src/
    ├── main/
    │   ├── java/com/pyeobugi/server/
    │   │   ├── PyeobugiServerApplication.java
    │   │   ├── common/
    │   │   │   ├── filter/        # TraceIdFilter, DeviceIdFilter, RateLimitFilter, BodySizeFilter
    │   │   │   ├── error/         # ErrorCode, ErrorResponse, ApiException, GlobalExceptionHandler, ErrorResponseWriter(필터용)
    │   │   │   └── time/          # KstClock, KstTimeUtils (KST 변환, 날짜/시간대 계산)
    │   │   ├── config/            # TimeConfig(Clock 빈), CorsConfig, JacksonConfig, OpenApiConfig, RateLimitProperties
    │   │   ├── event/
    │   │   │   ├── api/           # EventController, PostEventsResponse, RejectedEvent
    │   │   │   ├── validation/    # EventValidator, 타입별 PayloadValidator
    │   │   │   ├── domain/        # EventEntity(@Entity), EventType, ValidatedEvent
    │   │   │   ├── service/       # EventService (@Transactional, accepted/duplicated 계산)
    │   │   │   └── repository/    # EventRepository (JpaRepository + 네이티브 INSERT IGNORE)
    │   │   └── report/
    │   │       ├── api/           # ReportController, ReportSummaryResponse
    │   │       ├── service/       # ReportService, CommentPolicy, DistributionCalculator
    │   │       └── repository/    # ReportQueryRepository (네이티브 집계 쿼리), PeriodStats·HourlyStats (프로젝션)
    │   └── resources/
    │       ├── application.yml
    │       ├── application-local.yml
    │       ├── application-prod.yml
    │       └── db/migration/      # V1__create_events.sql ...
    └── test/
        ├── java/com/pyeobugi/server/
        │   ├── event/             # 검증 단위 테스트, 수집 API 통합 테스트
        │   ├── report/            # 집계 테스트
        │   └── contract/          # 예시 JSON 기반 계약 테스트
        └── resources/
            └── contract/          # AI 레포 docs/api/examples/*.json 복사본
```

---

## 6. 데이터베이스 설계

### 6-1. 스키마 (`V1__create_events.sql`)

```sql
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
```

### 6-2. 설계 의도

| 결정 | 이유 |
| --- | --- |
| `(device_id, event_id)` 유니크 키 | 재전송된 같은 이벤트를 중복 저장하지 않기 위한 멱등성(idempotency) 키 |
| 시각을 KST `DATETIME`으로 저장 | 서비스가 KST 전용이고, 기획자가 SQL로 직접 조회할 때 변환 없이 읽을 수 있음. 변환은 코드 한 곳(`KstTimeUtils`)에서만 처리 |
| `kst_date`, `kst_hour` 미리 계산 | 집계 시 JSON 파싱이나 시간 함수 없이 인덱스로 바로 조회 |
| `duration_sec` 등 컬럼 분리 | 집계에 쓰는 값은 컬럼으로, 나머지는 `payload` JSON으로 원본 보관 |
| 집계 기준은 `received_at`이 아니라 발생 시각 | 오프라인 버퍼링 후 일괄 전송되면 수신 시각이 실제 사용 시각과 다름 |
| IP 해시 컬럼 없음 | Rate Limit에만 쓰므로 저장할 필요가 없음 |

### 6-3. 시간 처리 원칙

- 시간대는 **세 곳 모두** `Asia/Seoul`로 고정한다.

| 위치 | 설정 |
| --- | --- |
| JVM | 메인 클래스에서 `TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"))` |
| 테스트 JVM | `build.gradle`의 `test` 태스크에 `systemProperty 'user.timezone', 'Asia/Seoul'` (`@SpringBootTest`는 `main()`을 거치지 않음) |
| DB 연결 | JDBC URL에 `connectionTimeZone=Asia/Seoul&forceConnectionTimeZoneToSession=true` |
| Hibernate | `spring.jpa.properties.hibernate.jdbc.time_zone: Asia/Seoul` |

- 코드에서는 `OffsetDateTime`으로 받아 `ZoneId.of("Asia/Seoul")`로 변환한 뒤 `LocalDateTime`으로 저장한다.
- 요청의 시각은 **`YYYY-MM-DDTHH:mm:ss+09:00` 형식만** 허용한다. 오프셋이 없는 시각, `Z`·다른 오프셋, 밀리초가 붙은 시각은 `INVALID_PAYLOAD`로 거절한다. 정규식으로 형식을 먼저 확인한 뒤 `OffsetDateTime`으로 파싱한다 (Jackson 기본 파서는 `Z`와 밀리초를 받아들이므로 그대로 쓰지 않는다).
- 응답의 시각(`generatedAt`, `turtleNeckTimeline.occurredAt`)도 같은 형식으로 내려준다.
- 리포트의 `date` 쿼리는 `uuuu-MM-dd` + `ResolverStyle.STRICT`로 파싱해 `2026-02-30`, `2026-1-5` 같은 값을 거절한다.
- KST는 서머타임이 없어서 날짜·시간 경계 계산이 단순하다.

### 6-4. JPA 설정과 엔티티 매핑

**`application.yml`** (공통)

```yaml
spring:
  application:
    name: pyeobugi-server
  profiles:
    default: local
  jpa:
    open-in-view: false        # 요청 끝까지 DB 연결을 잡아두지 않음
    hibernate:
      ddl-auto: none           # 스키마는 Flyway가 관리
    properties:
      hibernate:
        jdbc:
          time_zone: Asia/Seoul
  flyway:
    enabled: true
```

**DB 연결 정보** (프로필·환경별)

| 환경 | 연결 정보 출처 | 시간대 파라미터 넣는 곳 |
| --- | --- | --- |
| `local` | Docker Compose Support가 `compose.yaml`(MySQL 8.4, 호스트 포트 `13306` 고정)을 읽어 자동 구성. DB 툴 접속은 `127.0.0.1:13306`, `pyeobugi` / `pyeobugi-local` | `compose.yaml`의 `org.springframework.boot.jdbc.parameters` 라벨 |
| 테스트 | Testcontainers `@ServiceConnection` (MySQL 8.4) | `TestcontainersConfiguration`의 `withUrlParam` |
| `prod` | `application-prod.yml`의 `spring.datasource.url` + 환경 변수 `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` | URL에 직접 |

- 자동 구성(Docker Compose, Testcontainers)은 `spring.datasource.url`을 무시하고 URL을 직접 만들기 때문에, 시간대 파라미터를 위 표의 위치에 따로 넣어야 한다.
- `forceConnectionTimeZoneToSession`은 세션에 `Asia/Seoul` 이름으로 시간대를 지정하므로 MySQL에 시간대 테이블이 있어야 한다. 공식 Docker 이미지는 기본으로 넣어 주고, 운영 DB는 배포 때 확인한다.

- `ddl-auto`는 `validate`가 아니라 `none`으로 둔다. 스키마의 `CHAR(36)`, `TINYINT`, `JSON` 컬럼은 Hibernate가 기대하는 기본 타입과 달라 검증 오류가 나기 쉽다. 스키마 일치는 Testcontainers 통합 테스트로 확인한다.

**엔티티**

```java
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
```

- 카멜 케이스 필드(`deviceId`)는 Spring Boot 기본 명명 전략이 스네이크 케이스(`device_id`)로 바꿔 주므로 `@Column`이 필요 없다.
- 엔티티는 테이블 구조 표현과 리포지토리 생성 용도다. **저장에 `save()`를 쓰지 않는다** (8-3 참고).

**JPA의 `save()`를 쓰지 않는 이유**

| 상황 | `save()` 동작 | 문제 |
| --- | --- | --- |
| 같은 `eventId` 재전송 (PK가 별도 `id`) | 유니크 키 위반 예외 | 트랜잭션이 롤백 전용(rollback-only)으로 바뀌어 배치 전체 실패 |
| PK를 `eventId`로 잡은 경우 | 조회 후 있으면 `UPDATE` (merge) | 기존 데이터를 덮어써서 "덮어쓰지 않음" 규칙 위반 |

---

## 7. API 설계

### 7-1. 공통 규칙

**인증 헤더**

```
X-Device-Id: 3f2b8c1e-6a4d-4e0b-9b7a-0c1d2e3f4a5b
```

**에러 응답 포맷** (모든 실패 응답 공통)

```json
{ "code": "DEVICE_ID_INVALID", "message": "...", "traceId": "req-7f3a9c2e" }
```

- 형식은 `{ code, message?, traceId?, retryAfter? }`. FE는 `code`만 필수로 보고 나머지는 없어도 동작한다.
- 서버는 `code`, `message`, `traceId`를 항상 넣고, `retryAfter`는 429에서만 넣는다.
- 컨트롤러에서 난 예외는 `GlobalExceptionHandler`가, 필터에서 난 오류는 `ErrorResponseWriter`가 같은 형식으로 응답한다. 그 밖의 Spring MVC 표준 예외(JSON 파싱 실패, 파라미터 타입 오류 등)는 상태 코드 기준으로 400 → `INVALID_REQUEST`, 5xx → `INTERNAL_ERROR`로 바꾼다.
- 500 응답의 `message`에는 예외 내용을 넣지 않는다 (내부 정보 노출 방지). 예외는 서버 로그에만 남긴다.

| 상황 | HTTP | code | 클라이언트 처리 |
| --- | --- | --- | --- |
| 기기 ID 누락, UUID 형식 위반 | 400 | `DEVICE_ID_INVALID` | 버그로 보고 배치를 남긴 채 재시도 |
| 요청 구조 오류 (최상위 모르는 필드, `events` 빈 배열 등), 리포트 `date` 오류 | 400 | `INVALID_REQUEST` | 배치 폐기 |
| 존재하지 않는 경로 | 404 | `NOT_FOUND` | 버그로 보고 확인 (FE 초안에 없던 코드) |
| 지원하지 않는 HTTP 메서드 | 405 | `METHOD_NOT_ALLOWED` | 버그로 보고 확인 (FE 초안에 없던 코드) |
| 이벤트 101개 이상, 본문 64KB 초과 | 413 | `PAYLOAD_TOO_LARGE` | 배치를 반으로 나눠 재전송, 1개짜리면 폐기 |
| 호출 한도 초과 | 429 | `RATE_LIMITED` | `retryAfter`초 후 재시도 |
| 서버 오류 | 5xx | `INTERNAL_ERROR` | 큐 유지 후 재시도 |

- `traceId`는 요청마다 생성하고 서버 로그에도 같이 남긴다. 형식은 `req-` + 랜덤 8자리 16진수를 제안한다 (서버 재시작 후에도 겹치지 않음).
- 429 응답은 본문의 `retryAfter`(정수, 초)와 `Retry-After` 헤더를 **둘 다** 내려준다. 클라이언트는 본문을 먼저 보고, 없으면 헤더를 본다.

### 7-2. `POST /api/events`

**요청**

```json
{
  "events": [
    {
      "eventId": "b1c2d3e4-0000-4000-8000-000000000003",
      "type": "USAGE_SEGMENT",
      "sessionId": "a9a9a9a9-0000-4000-8000-0000000000aa",
      "occurredAt": "2026-10-04T09:23:00+09:00",
      "payload": {
        "startedAt": "2026-10-04T09:22:00+09:00",
        "endedAt": "2026-10-04T09:23:00+09:00",
        "durationSec": 60,
        "badDurationSec": 12
      }
    }
  ]
}
```

**이벤트 종류와 payload 화이트리스트**

| type | payload 허용 필드 | 집계 날짜 기준 | 용도 |
| --- | --- | --- | --- |
| `SESSION_START` | 없음 (`{}`) | `occurredAt` | 실행 이력 |
| `SESSION_END` | 없음 (`{}`) | `occurredAt` | 실행 이력 |
| `USAGE_SEGMENT` | `startedAt`, `endedAt`, `durationSec`, `badDurationSec` | `startedAt` | 사용 시간, 유지율, 시간대별 차트, 자세 분포 |
| `POSTURE_BAD` | `severity` | `occurredAt` | 거북목 횟수, 감지 시점 타임라인 |
| `POMODORO_COMPLETED` | 없음 (`{}`) | `occurredAt` | 뽀모도로 완료 수 |
| `CALIBRATION_COMPLETED` | `isRecalibration` | `occurredAt` | 온보딩 완료 로그 |
| `BUBBLE_SHOWN` | `bubbleType` | `occurredAt` | 말풍선 표시 로그 |
| `SETTINGS_CHANGED` | `studyTimer`, `stretch`, `awayPause`, `overlay` | `occurredAt` | 기능 on/off 비율 |

- 이벤트 공통 필드: `eventId`, `type`, `sessionId`, `occurredAt`, `payload`
- 최상위 body, 이벤트 공통 필드, 타입별 payload 모두 화이트리스트(strict)로 검사한다.
- `severity` 허용 값은 AI 레포의 `src/shared/posture/score.ts`에서 확인한 뒤 확정한다.
- `bubbleType` 허용 값: `POSTURE`, `STRETCH`, `TIMER_BREAK`, `ENCOURAGE`, `GREETING`

**응답 200**

```json
{
  "accepted": 1,
  "duplicated": 1,
  "rejected": [
    { "index": 2, "eventId": "b1c2d3e4-0000-4000-8000-000000000003", "code": "INVALID_PAYLOAD" }
  ]
}
```

| rejected code | 의미 |
| --- | --- |
| `INVALID_PAYLOAD` | 공통 필드·payload 형식, 값 오류 |
| `FORBIDDEN_FIELD` | 허용되지 않은 필드 포함 (다른 오류와 같이 있어도 이 code 우선) |
| `UNKNOWN_TYPE` | 정의되지 않은 `type` |
| `OCCURRED_AT_OUT_OF_RANGE` | 시각 허용 범위 밖 |

**응답 규칙**

1. `accepted + duplicated + rejected 개수 = 요청 이벤트 수`
2. `accepted`는 **DB 커밋까지 끝난** 이벤트 수다. 응답 이후의 리포트 조회에는 반드시 반영되어 있어야 한다.
3. 같은 `eventId`로 내용이 다른 이벤트가 와도 덮어쓰지 않고 `duplicated`로 센다. 같은 배치 안의 중복도 두 번째부터 `duplicated`다.
4. `rejected.index`는 0부터 시작하고, `eventId`가 없거나 형식이 틀리면 응답에서 `eventId`를 생략한다.
5. 이벤트 하나의 문제로 배치 전체를 400 처리하지 않는다. 400은 최상위 구조가 깨졌을 때만 쓴다.

### 7-3. `GET /api/reports/summary`

FE 26-10-27 수정안(`hasData`, `generatedAt`, `date`, `day`, `week`, `hourly`, `comment`)을 **기본 응답**으로 한다. v2에서 제안했던 확장 필드(`yesterday`, `lastWeek`, `turtleNeckTimeline`, `distribution`)는 요청 날짜 기준으로 정의만 바꿔 두고, FE 수요가 확인되면 **추가만** 한다 (14장 #19).

**요청**

```
GET /api/reports/summary?date=2026-10-04
X-Device-Id: 3f2b8c1e-6a4d-4e0b-9b7a-0c1d2e3f4a5b
```

| 쿼리 | 규칙 |
| --- | --- |
| `date` | KST 날짜 `YYYY-MM-DD`, 선택. 생략하면 서버 기준 KST 오늘 |
| 잘못된 `date` | 형식 오류(`2026-1-5`), 존재하지 않는 날짜(`2026-02-30`) → `400 INVALID_REQUEST` |
| 미래 `date` | **(제안)** 400이 아니라 `200` + 빈 응답(`hasData: false`). 클라이언트 시계가 서버보다 앞선 자정 직후에 오늘 화면이 400으로 깨지지 않게 하기 위함 (14장 #20) |

**응답 200**

```json
{
  "hasData": true,
  "generatedAt": "2026-10-04T14:05:00+09:00",
  "date": "2026-10-04",
  "day":  { "postureKeepRate": 82, "turtleNeckCount": 5,  "pomodoroCount": 6,  "usageSeconds": 12600 },
  "week": { "postureKeepRate": 78, "turtleNeckCount": 31, "pomodoroCount": 22, "usageSeconds": 71400 },
  "hourly": [
    { "hour": 0, "postureKeepRate": null },
    { "hour": 9, "postureKeepRate": 70 },
    { "hour": 23, "postureKeepRate": null }
  ],
  "comment": { "message": "오늘 잘했어요", "mood": "GOOD" }
}
```

> `hourly`는 지면상 일부만 적었고, 실제로는 0~23시 24개가 모두 들어간다.

**응답 200 — 요청 날짜에 기록 없음**

```json
{
  "hasData": false,
  "generatedAt": "2026-10-09T20:00:00+09:00",
  "date": "2026-10-09",
  "day":  { "postureKeepRate": null, "turtleNeckCount": 0, "pomodoroCount": 0, "usageSeconds": 0 },
  "week": { "postureKeepRate": 76, "turtleNeckCount": 20, "pomodoroCount": 9, "usageSeconds": 40200 },
  "hourly": [ { "hour": 0, "postureKeepRate": null }, "... 24개 모두 null ..." ],
  "comment": null
}
```

- `hasData`가 `day` 기준이므로, 그날 기록이 없어도 **`week`에는 같은 주 다른 날의 값이 들어갈 수 있다.** 빈 상태 화면에서 `week` 카드를 보여줄지는 FE가 정한다.

**필드 정의**

| 필드 | 정의 |
| --- | --- |
| `hasData` | **요청 날짜**에 `duration_sec > 0`인 `USAGE_SEGMENT`, `POSTURE_BAD`, `POMODORO_COMPLETED` 중 하나라도 있으면 `true`. `day` 집계값으로 판정 (`usageSeconds > 0 \|\| turtleNeckCount > 0 \|\| pomodoroCount > 0`) |
| `generatedAt` | 서버 응답 생성 시각 (`+09:00`, 초 단위) |
| `date` | 요청한 날짜 echo. `date`를 생략했으면 서버가 사용한 오늘 날짜 |
| `day` | 요청 날짜 하루치 (KST 00:00 ~ 24:00, 오늘이면 결과적으로 현재까지) |
| `week` | 요청 날짜가 속한 주의 **월요일 00:00(KST) ~ 요청 날짜 24:00**. 오늘이면 기존(월요일 ~ 현재)과 같음 |
| `postureKeepRate` | `(Σduration − Σbad) ÷ Σduration × 100`, **마지막에 한 번만 반올림**. 사용 시간이 0이면 `null` |
| `turtleNeckCount` | `POSTURE_BAD` 개수 (고개 숙임 포함, AI ADR 0002) |
| `pomodoroCount` | `POMODORO_COMPLETED` 개수 |
| `usageSeconds` | Σ`duration_sec` |
| `hourly` | 요청 날짜의 시간대별 유지율. **항상 24개**, `hour` 0~23 오름차순. 해당 시간대 Σ`duration_sec`이 0이면 `postureKeepRate: null`. 산식은 `postureKeepRate`와 같음. 시간대는 `kst_hour`(= `startedAt`의 시) 기준 (8-4) |
| `comment` | 서버가 판정한 문구와 `mood`. `day` 사용 시간이 0이면 `null` |

- 차트 색 구간(70 미만 / 70~80 / 80 초과)은 FE가 정한다.
- "어제 대비", "지난주 대비" 차이(diff)는 **FE가 계산**한다. 유지율 %p 차이는 반올림된 값끼리 뺀다. 어제 값은 `date` 파라미터로 한 번 더 조회해서 얻을 수 있다.
- `Cache-Control: no-store`
- 서버 처리 시간 목표 1초 이내

**확장 필드 (보류, 14장 #19)**

| 필드 | 정의 (요청 날짜 `D` 기준) |
| --- | --- |
| `yesterday` | `D − 1일` 하루 |
| `lastWeek` | `D`가 속한 주의 지난주 (전체 vs 같은 요일까지, 14장 #7) |
| `turtleNeckTimeline.occurredAt` | `D`의 `POSTURE_BAD` 발생 시각, 시간순 |
| `turtleNeckTimeline.peakHours` | 시간대별 개수 상위 구간 (14장 #9) |
| `distribution` | `D`의 자세 분포 (8-6). `D` 사용 시간이 0이면 `null` |

---

## 8. 핵심 구현 설계

### 8-1. 요청 처리 순서

```
요청
 │
 ├─ ① TraceIdFilter       traceId 생성, MDC에 등록 (모든 로그에 traceId 포함)
 ├─ ② DeviceIdFilter      X-Device-Id 존재·UUID v4 형식 검사 → 실패 시 400 DEVICE_ID_INVALID
 ├─ ③ RateLimitFilter     기기 ID 버킷 + IP 해시 버킷 검사 → 실패 시 429 RATE_LIMITED
 ├─ ④ BodySizeFilter      본문 64KB 초과 시 파싱 전에 413 PAYLOAD_TOO_LARGE
 │
 └─ Controller
      ├─ POST /api/events            → 8-2, 8-3
      └─ GET  /api/reports/summary   → 8-5, 8-6
```

**필터 규칙** (순서 상수는 `FilterOrders`)

| 필터 | 적용 범위 | 규칙 |
| --- | --- | --- |
| `TraceIdFilter` | 모든 요청 | `req-` + 16진수 8자리를 MDC `traceId`에 넣고 요청이 끝나면 지운다. 로그 패턴(`logging.pattern.correlation`)으로 모든 로그 줄에 `[req-xxxxxxxx]`가 붙는다 |
| `DeviceIdFilter` | `/api/**`, `OPTIONS` 제외 | UUID v4(대소문자 무관)가 아니면 400 `DEVICE_ID_INVALID`. 통과하면 **소문자로 바꿔** 요청 속성 `deviceId`에 담는다. 대문자로 보낸 기기가 다른 기기로 집계되지 않게 하기 위함 |

> 기기 ID 대소문자 처리는 mock `validate.ts`와 다를 수 있다. 0단계 분석 때 확인한다.

### 8-2. 이벤트 검증기

초안의 규칙은 **최상위 위반은 400, 이벤트 내부 위반은 해당 이벤트만 `rejected`** 다. Jackson의 `FAIL_ON_UNKNOWN_PROPERTIES` 하나로 처리하면 이벤트 내부 위반도 배치 전체 400이 되므로, 아래처럼 두 단계로 나눈다.

**1단계: 최상위 구조 검증** (위반 시 `400 INVALID_REQUEST`, 전체 미저장)

- 본문이 JSON 객체이고 필드가 `events` 하나뿐인지
- `events`가 배열이고 길이가 1 이상인지 (100 초과는 `413`)

**2단계: 이벤트별 검증** (`events`는 `JsonNode`로 받아 하나씩 직접 검사)

FE 수정안에 따라 **금지 필드가 다른 오류와 같이 있으면 `FORBIDDEN_FIELD`가 우선**이다. 그래서 금지 필드 검사(1, 3)를 값 검사(4, 5)보다 앞에 둔다.

| 순서 | 검사 | 실패 code |
| --- | --- | --- |
| 1 | 이벤트가 객체인지, 공통 필드 외 필드가 없는지 | `FORBIDDEN_FIELD` |
| 2 | `type`이 정의된 값인지 (타입을 알아야 payload 화이트리스트를 정할 수 있음) | `UNKNOWN_TYPE` |
| 3 | payload에 타입별 허용 필드 외 필드가 없는지 | `FORBIDDEN_FIELD` |
| 4 | `eventId`, `sessionId`가 UUID v4인지, `occurredAt`이 `+09:00`·초 단위 형식인지, payload 값의 형식과 범위 | `INVALID_PAYLOAD` |
| 5 | `occurredAt`(`USAGE_SEGMENT`는 `startedAt`·`endedAt`도)이 현재 +5분 ~ 과거 30일 범위인지 | `OCCURRED_AT_OUT_OF_RANGE` |

- 이벤트가 객체가 아니면 `INVALID_PAYLOAD`, `type`이 없거나 문자열이 아니면 `UNKNOWN_TYPE`으로 둔다.
- 타입을 모르면 payload 화이트리스트를 정할 수 없으므로, `type`이 틀린 이벤트의 payload 금지 필드는 `UNKNOWN_TYPE`으로 응답된다.

> `FORBIDDEN_FIELD` 외의 순서와 `eventId` 형식 오류 시의 code는 **AI 레포 `src/main/mock/validate.ts`의 동작에 맞춘다.** 위 표는 초안이며, 옮기면서 다른 점이 있으면 mock 쪽을 따른다.

**`USAGE_SEGMENT` 추가 규칙** (위반 시 `INVALID_PAYLOAD`)

- `0 ≤ badDurationSec ≤ durationSec ≤ 3600`
- `startedAt < endedAt`
- `startedAt`과 `endedAt`이 같은 KST 날짜 (자정 종료는 다음 날 `00:00:00` 허용)
- `durationSec = endedAt − startedAt` (±1초 허용)
- `occurredAt = endedAt`
- `startedAt`, `endedAt`에도 시각 허용 범위 적용
- (합의 후 적용) `startedAt`과 `endedAt`이 같은 KST 시간대 (정시 종료 허용)
- (합의 후 적용) `0 ≤ mildDurationSec ≤ badDurationSec`

> 합의 후 적용 규칙은 클라이언트가 해당 동작을 구현하기 **전에 켜면 정상 이벤트가 전부 거절된다.** 설정값(feature flag)으로 분리해 두고 클라이언트 배포 후에 켠다.

### 8-3. 멱등 저장

검증을 통과한 이벤트를 **하나의 트랜잭션 안에서 이벤트마다 네이티브 `INSERT IGNORE`** 로 저장한다. 반환값이 1이면 새로 저장, 0이면 중복이다.

**리포지토리**

```java
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
```

**서비스**

```java
@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final KstClock clock;

    @Transactional
    public SaveResult save(String deviceId, List<ValidatedEvent> events) {
        LocalDateTime receivedAt = clock.now();
        int accepted = 0;
        for (ValidatedEvent e : events) {
            accepted += eventRepository.insertIgnore(
                deviceId, e.eventId(), e.sessionId(), e.type().name(),
                e.occurredAt(), e.kstDate(), e.kstHour(),
                e.durationSec(), e.badDurationSec(), e.mildDurationSec(),
                e.payloadJson(), receivedAt);
        }
        return new SaveResult(accepted, events.size() - accepted);
    }
}
```

- `accepted` = 반환값 1의 합, `duplicated` = 검증 통과 수 − `accepted`
- 같은 배치 안의 중복 `eventId`도 두 번째 삽입이 0을 반환하므로 자연스럽게 `duplicated`가 된다.
- `@Modifying` 쿼리는 트랜잭션 안에서만 실행되므로 서비스의 `@Transactional`이 필수다. 메서드가 끝나며 커밋된 뒤 컨트롤러가 응답하므로 "`accepted`는 커밋 완료된 수" 규칙이 지켜진다.
- 배치당 최대 100번 실행되지만 하나의 트랜잭션 안이라 이 규모에서는 문제없다.

**주의 사항**

| 항목 | 내용 |
| --- | --- |
| `INSERT IGNORE`의 부작용 | 중복 키 외의 오류(값 범위 초과 등)도 경고로 바꿔 무시한다. 그래서 **8-2 검증으로 모든 값을 미리 걸러내는 것이 전제**다 |
| `save()` 미사용 | 6-4의 이유로 저장에는 JPA `save()`를 쓰지 않는다 |
| 실제 카운트 확인 | **Testcontainers로 중복 재전송 테스트를 돌려 반환값이 1/0으로 나오는지 확인**한다 |

### 8-4. 시간대 집계값 계산

| 이벤트 | `kst_date` | `kst_hour` |
| --- | --- | --- |
| `USAGE_SEGMENT` | `startedAt`의 KST 날짜 | `startedAt`의 KST 시 |
| 그 외 | `occurredAt`의 KST 날짜 | `occurredAt`의 KST 시 |

현재 클라이언트는 `USAGE_SEGMENT`를 1분마다 만들고 날짜 경계에서만 끊는다. 시간 경계를 넘는 구간(예: `09:59:30 ~ 10:00:30`)은 **나누지 않고 전부 `startedAt` 시간대(9시)로 집계**한다 (FE와 합의). 그래서 **시간대마다 최대 1분 정도의 오차**가 생길 수 있다. 데모에는 큰 문제가 없지만, 정확도를 위해 **매 정시 분할**을 AI 파트에 요청한다 (14장 #5). 클라이언트가 정시 분할을 해도 서버 집계 코드는 바뀌지 않는다.

`hourly`를 이 기준으로 만들기 때문에, 시간대별 Σ`duration_sec`을 모두 더하면 항상 `day.usageSeconds`와 같다 (구간이 두 시간대에 나뉘어 들어가지 않음).

`POSTURE_BAD`의 `occurredAt`은 판정 시각이 아니라 나쁜 자세가 **시작된** 시각으로 되돌려 기록되므로, 배치 안에서 시각 순서가 뒤섞일 수 있다. 서버는 배치 내 순서를 가정하지 않는다.

### 8-5. 집계 쿼리

집계는 네이티브 쿼리로 작성하고, 결과는 엔티티가 아니라 **인터페이스 프로젝션(interface projection)** 으로 받는다. SQL 컬럼 별칭(alias)과 getter 이름을 맞추면 자동으로 매핑된다.

**프로젝션**

```java
public interface PeriodStats {
    Long getUsageSec();
    Long getBadSec();
    Long getMildSec();
    Long getMildRows();
    Long getTurtleCount();
    Long getPomodoroCount();
}

public interface HourlyStats {
    Integer getKstHour();
    Long getUsageSec();
    Long getBadSec();
}
```

**리포지토리**

```java
public interface ReportQueryRepository extends Repository<EventEntity, Long> {

    /** 기간 지표: day(from = to = date), week(from = 그 주 월요일, to = date)에 기간만 바꿔 호출 */
    @Query(value = """
        SELECT
          CAST(COALESCE(SUM(duration_sec), 0)      AS SIGNED) AS usageSec,
          CAST(COALESCE(SUM(bad_duration_sec), 0)  AS SIGNED) AS badSec,
          CAST(COALESCE(SUM(mild_duration_sec), 0) AS SIGNED) AS mildSec,
          COUNT(mild_duration_sec)                            AS mildRows,
          CAST(COALESCE(SUM(type = 'POSTURE_BAD'), 0)        AS SIGNED) AS turtleCount,
          CAST(COALESCE(SUM(type = 'POMODORO_COMPLETED'), 0) AS SIGNED) AS pomodoroCount
        FROM events
        WHERE device_id = :deviceId
          AND kst_date BETWEEN :from AND :to
          AND type IN ('USAGE_SEGMENT', 'POSTURE_BAD', 'POMODORO_COMPLETED')
        """, nativeQuery = true)
    PeriodStats findPeriodStats(@Param("deviceId") String deviceId,
                                @Param("from") LocalDate from,
                                @Param("to") LocalDate to);

    /** 시간대별 유지율: 기록 있는 시간대만 반환, 24칸 채우기는 Java에서 */
    @Query(value = """
        SELECT kst_hour                              AS kstHour,
               CAST(SUM(duration_sec) AS SIGNED)     AS usageSec,
               CAST(SUM(bad_duration_sec) AS SIGNED) AS badSec
        FROM events
        WHERE device_id = :deviceId AND kst_date = :date
          AND type = 'USAGE_SEGMENT' AND duration_sec > 0
        GROUP BY kst_hour
        ORDER BY kst_hour
        """, nativeQuery = true)
    List<HourlyStats> findHourlyStats(@Param("deviceId") String deviceId,
                                      @Param("date") LocalDate date);

    /** 거북목 감지 시점 (확장 필드 turtleNeckTimeline용) */
    @Query(value = """
        SELECT occurred_at FROM events
        WHERE device_id = :deviceId AND kst_date = :date AND type = 'POSTURE_BAD'
        ORDER BY occurred_at
        """, nativeQuery = true)
    List<LocalDateTime> findTurtleNeckTimes(@Param("deviceId") String deviceId,
                                            @Param("date") LocalDate date);
}
```

- MySQL의 `SUM()`은 결과를 `DECIMAL`로 돌려주므로, 프로젝션의 `Long` getter와 타입이 어긋나지 않도록 SQL에서 `CAST(... AS SIGNED)`로 정수 변환한다.
- `hasData`는 요청 날짜 기준이라 별도 쿼리 없이 `day`의 `PeriodStats`로 판정한다 (`usageSec > 0 || turtleCount > 0 || pomodoroCount > 0`). `USAGE_SEGMENT`의 `duration_sec`이 0이면 `usageSec`에 더해지지 않으므로 기존 `duration_sec > 0` 조건과 같다.
- 요청 한 번에 쿼리는 `day`, `week`, `hourly` 3번이다.

계산 로직(Java)

- 기간: `day` = `date ~ date`, `week` = `date.with(DayOfWeek.MONDAY) ~ date`. 미래 날짜면 쿼리 없이 빈 응답 (7-3)
- `hourly`: 길이 24 배열을 `null`로 채운 뒤 쿼리 결과의 `kstHour` 칸만 유지율로 채운다
- 유지율: `Math.round((usage − bad) * 100.0 / usage)`, `usage == 0`이면 `null`
- 주간 유지율은 일별 유지율의 평균이 아니라 **주간 합계**로 계산
- `peakHours`: 거북목 시점 목록을 시간대별로 세어 상위 구간 추출
- 반올림은 `RoundingMode.HALF_UP`으로 통일하고, mock의 `summary.ts`와 결과가 같은지 테스트로 확인

### 8-6. 자세 분포와 코멘트

**자세 분포**

| 구간 | 계산 |
| --- | --- |
| 바른 자세 | `duration − bad` |
| 살짝 숙임 | `mild` (데모는 `null`) |
| 거북목 | `bad − mild` (데모는 `bad`) |

- 비율은 바른 자세를 항상 `postureKeepRate`와 같은 값으로 고정하고, 나머지는 **최대 잔여 방식(largest remainder method)** 으로 반올림해 합이 100이 되게 맞춘다.
- `mild_rows = 0`이면 2단계로 응답한다 (`mild*` = `null`).

**코멘트 판정** (mock 동작과 동일하게 시작, 값은 설정 파일로 분리)

| `day` 유지율 | mood | 문구 (예시) |
| --- | --- | --- |
| 80% 이상 | `GOOD` | 오늘 잘했어요 |
| 50% 이상 | `NORMAL` | (기획 확정 필요) |
| 50% 미만 | `BAD` | (기획 확정 필요) |
| `day` 사용 시간 0 | — | `comment: null` |

- 과거 날짜를 조회해도 문구가 "오늘 ..."로 나가지 않도록 문구 확정 시 같이 정한다 (14장 #8).

### 8-7. Rate Limit과 IP 해시

| 기준 | 제안값 | 근거 |
| --- | --- | --- |
| 기기 ID | 분당 120회 (순간 최대 60회) | 평상시 10초 주기라 분당 6회 정도. 오프라인 후 일괄 동기화(큐 5,000개 ÷ 100개 = 최대 50회 연속)를 수용 |
| IP 해시 | 분당 1,200회 | 학교 Wi-Fi처럼 여러 기기가 같은 IP를 쓰는 경우를 고려 |

- 제안값은 `application.yml`로 분리해 운영 중에 조정할 수 있게 한다.
- IP 해시: **HMAC-SHA256 + 서버 비밀키**. IPv4는 약 43억 개뿐이라 단순 SHA-256은 전수 대입으로 원본 복원이 가능하다.
- 리버스 프록시(reverse proxy) 뒤에서는 신뢰하는 프록시가 넣은 `X-Forwarded-For`만 사용한다 (`server.forward-headers-strategy` 설정).

### 8-8. 로깅

- 요청 본문은 로그에 남기지 않는다. 남기는 것: traceId, 메서드, 경로, 상태 코드, 처리 시간, 이벤트 수, accepted/duplicated/rejected 수
- 기기 ID는 앞 8자리만 남긴다 (디버깅용)

### 8-9. CORS

- 이벤트 전송은 Electron main 프로세스(Node.js)에서 하므로 CORS 대상이 아니다.
- 리포트 조회도 main 프로세스에서 하는지 AI 파트에 확인한다. 렌더러(renderer)에서 직접 호출한다면 `X-Device-Id`가 커스텀 헤더라서 프리플라이트(preflight) `OPTIONS` 요청이 생기므로, 개발 서버 origin과 패키징 앱 origin을 허용 목록에 넣는다.

---

## 9. AI 파트(클라이언트)와의 연동

### 9-1. 연결 방식

코드끼리 직접 연결하지 않는다. 두 파트는 **HTTP API로만 통신**하며, 연결에 필요한 것은 서버 주소와 JSON 형식뿐이다.

| 연결 지점 | 클라이언트 | 서버 |
| --- | --- | --- |
| 서버 주소 | 빌드 시 `MAIN_VITE_API_BASE_URL` 환경 변수 | 로컬 `http://localhost:8080`, 배포 HTTPS 도메인 |
| 이벤트 형식 | `src/shared/events.ts` | 요청 DTO, 이벤트 검증기 |
| 응답 형식 | `src/shared/api.ts` | 응답 DTO |
| 검증 규칙 | `src/main/mock/validate.ts` (mock 참고 구현) | `EventValidator` |
| 집계 산식 | `src/main/mock/summary.ts` (mock 참고 구현) | `ReportService` |
| 예시 데이터 | `docs/api/examples/*.json` | 계약 테스트 픽스처(fixture) |

### 9-2. 참고 구현을 옮기는 이유

클라이언트는 개발 중 mock 서버를 상대로 동작을 맞춰 왔고, 배포 후에는 Spring 서버가 그 자리를 대신한다. 서버 응답에 따라 클라이언트가 이벤트를 지우거나 버리거나 재시도하므로, **판정이 다르면 데이터가 조용히 유실된다.**

| mock 판정 | Spring 판정이 다를 때 | 결과 |
| --- | --- | --- |
| 정상 저장 | `rejected` | 재시도하지 않으므로 데이터 유실 |
| 이벤트 하나만 `rejected` | 배치 전체 400 | 정상 이벤트까지 배치째 폐기 |
| `rejected` | 정상 저장 | 잘못된 값이 들어가 리포트 수치 오류 |

코드를 한 줄씩 번역하는 것이 아니라, **같은 입력에 같은 응답을 주도록 규칙을 맞추는 것**이 목표다.

### 9-3. 로컬 연동 절차

1. Spring 서버를 `local` 프로필로 실행 (`localhost:8080`)
2. AI 레포를 클론하고 환경 변수를 지정해 개발 모드로 실행
   ```bash
   # macOS / Linux
   MAIN_VITE_API_BASE_URL=http://localhost:8080 npm run dev
   ```
   ```powershell
   # Windows PowerShell
   $env:MAIN_VITE_API_BASE_URL="http://localhost:8080"; npm run dev
   ```
3. 앱에서 기준 잡기 → 고개 숙이기 → 1분 이상 사용 → 리포트 열기
4. `SELECT * FROM events ORDER BY id DESC LIMIT 20;`으로 저장 확인, 앱 리포트 수치와 비교

> 개발 모드에서는 mock이 켜지므로, 환경 변수만으로 실제 서버로 가는지 mock을 따로 꺼야 하는지 AI 파트에 확인한다 (14장).

---

## 10. 테스트 전략

### 10-1. 테스트 종류

| 종류 | 도구 | 대상 |
| --- | --- | --- |
| 단위 테스트 | JUnit 5 | 이벤트 검증기, KST 시간 계산, 유지율·분포 반올림, 코멘트 판정 |
| 통합 테스트 | MockMvc + Testcontainers(MySQL) | 필터 체인, 수집·조회 API 전체 흐름, `insertIgnore` 반환값(1/0), 엔티티와 Flyway 스키마 일치, 프로젝션 매핑 |
| 계약 테스트 | MockMvc + 예시 JSON | AI 레포 `docs/api/examples/`의 요청을 넣고 응답이 예시와 같은지 비교 |
| 수동 테스트 | IntelliJ HTTP Client (`.http`) | 개발 중 빠른 확인, 팀 공유 |
| 연동 테스트 | 실제 클라이언트 | 9-3 절차 |

### 10-2. 계약 테스트 대상

| 예시 파일 | 확인 내용 |
| --- | --- |
| `post-events.request.json` / `.response.json` | 세션 전체(시작, 기준 잡기, 나쁜 자세, 자정에서 나뉜 구간 4개, 종료) 저장 |
| `post-events-mixed.*` | 중복 1, 정상 1, 금지 필드(`payload.pitch`) 1 → `accepted 1 / duplicated 1 / rejected 1` |
| `reports-summary.response.json` | 위 이벤트 저장 후 리포트 |
| `reports-summary-empty.response.json` | 새 기기 → `hasData: false`, `hourly` 24개 모두 `null` |
| `error-device-id.response.json` | 잘못된 기기 ID → 400 `DEVICE_ID_INVALID` |

- 시각이 들어간 예시는 테스트 시계(`Clock`)를 고정해서 비교한다. 서버는 `KstClock`을 주입받아 현재 시각을 얻도록 만든다.
- `traceId`, `generatedAt`처럼 매번 달라지는 값은 비교에서 제외한다.

### 10-3. 경계 사례 체크리스트

- [ ] 같은 배치 재전송 → 전부 `duplicated`
- [ ] 같은 `eventId`, 다른 내용 → `duplicated`, 기존 데이터 유지
- [ ] 같은 배치 안의 중복 `eventId`
- [ ] `accepted + duplicated + rejected = 요청 수`
- [ ] 자정 종료 구간 (`23:59:00 ~ 다음 날 00:00:00`) 허용
- [ ] 자정을 넘는 구간 거절
- [ ] `durationSec` ±1초 허용, ±2초 거절
- [ ] `occurredAt` 현재 +5분 허용, +5분 1초 거절
- [ ] `occurredAt` 과거 30일 허용, 30일 초과 거절
- [ ] 이벤트 100개 허용, 101개 413
- [ ] 본문 64KB 경계
- [ ] 최상위에 `events` 외 필드 → 400
- [ ] payload에 `pitch` 포함 → `FORBIDDEN_FIELD`
- [ ] 오프셋 없는 시각 → 거절
- [ ] `Z`, `+00:00` 등 `+09:00` 외 오프셋 → `INVALID_PAYLOAD`
- [ ] 밀리초 포함 시각 (`...:00.000+09:00`) → `INVALID_PAYLOAD`
- [ ] 금지 필드 + 다른 오류(예: `durationSec` 불일치) 동시 → `FORBIDDEN_FIELD`
- [ ] `events` 빈 배열 → 400 `INVALID_REQUEST`
- [ ] 유지율 반올림 (예: 78.5 → 79), 사용 시간 0 → `null`
- [ ] 주간 유지율이 일별 평균이 아니라 합계 기준인지
- [ ] 자세 분포 비율 합 100
- [ ] 월요일 00:00 주간 경계
- [ ] Rate Limit 초과 시 429 + 본문 `retryAfter` + `Retry-After` 헤더
- [ ] `date` 생략 → 오늘, 응답 `date`에 오늘 날짜
- [ ] `date=2026-02-30`, `date=2026-1-5`, `date=abc` → 400 `INVALID_REQUEST`
- [ ] 미래 `date` → 200 + `hasData: false`
- [ ] 과거 날짜(수요일) 조회 시 `week` = 그 주 월요일 ~ 수요일, 목요일 이후 기록 미포함
- [ ] 일요일 조회 시 `week`에 그 주 월~일 포함, 다음 날(월요일) 조회 시 새 주
- [ ] `hourly` 항상 24개, 기록 없는 시간 `null`
- [ ] `09:59:30 ~ 10:00:30` 구간 → 9시로 집계
- [ ] 요청 날짜에 기록 없고 같은 주 다른 날에 기록 있음 → `hasData: false`, `week`는 값 있음
- [ ] `POSTURE_BAD`만 있는 날 → `hasData: true`, `day.postureKeepRate: null`

---

## 11. 배포와 운영

### 11-1. 환경 구성

| 프로필 | 용도 | DB | CORS 허용 |
| --- | --- | --- | --- |
| `local` | 개인 개발 | Docker MySQL | 개발 서버 origin |
| `test` | 자동 테스트 | Testcontainers MySQL | — |
| `prod` | 배포 | 운영 MySQL | 패키징 앱 origin (필요 시) |

비밀값(DB 비밀번호, IP 해시 키)은 환경 변수로 주입하고 Git에 올리지 않는다.

### 11-2. 배포 구성 (안)

```
인터넷 ── HTTPS(443) ──▶ Nginx (TLS 종료, Let's Encrypt 인증서)
                              │ HTTP(8080, 내부)
                              ▼
                         Spring Boot
                              │ 3306 (내부망만)
                              ▼
                            MySQL
```

- 서버 1대에 Docker Compose로 올리는 구성이 가장 단순하다. 클라우드 선택은 14장에서 확정한다.
- MySQL 포트는 외부에 열지 않는다.

### 11-3. 기획자 DB 접근

| 항목 | 내용 |
| --- | --- |
| 계정 | 기획자 전용 읽기 전용 계정 (`SELECT` 권한만) |
| 접속 | SSH 터널(SSH tunnel) 경유. MySQL Workbench나 DBeaver의 SSH 터널 기능 사용 |
| 문서 | 12장의 SQL과 접속 방법을 정리해 전달 |

### 11-4. 데이터 보관

이벤트 수신은 과거 30일까지, 리포트는 지난주까지만 사용한다. 운영 분석 기간을 고려해 보관 기간을 정하고(14장), 기간이 지난 데이터는 주기적으로 삭제한다.

---

## 12. 운영 SQL

```sql
-- 1) 일별 활성 기기 수 (실제로 감지를 사용한 기기)
SELECT kst_date, COUNT(DISTINCT device_id) AS active_devices
FROM events
WHERE type = 'USAGE_SEGMENT' AND duration_sec > 0
GROUP BY kst_date
ORDER BY kst_date DESC;

-- 2) 일별 기기당 평균 사용 시간(분)과 전체 정자세 유지율(%)
SELECT kst_date,
       ROUND(SUM(duration_sec) / COUNT(DISTINCT device_id) / 60, 1)                  AS avg_usage_min,
       ROUND((SUM(duration_sec) - SUM(bad_duration_sec)) / SUM(duration_sec) * 100) AS keep_rate
FROM events
WHERE type = 'USAGE_SEGMENT' AND duration_sec > 0
GROUP BY kst_date
ORDER BY kst_date DESC;

-- 3) 온보딩(최초 캘리브레이션) 완료 기기 수
SELECT COUNT(DISTINCT device_id) AS onboarded_devices
FROM events
WHERE type = 'CALIBRATION_COMPLETED'
  AND JSON_EXTRACT(payload, '$.isRecalibration') = false;

-- 4) 기능 on/off 비율 (기기별 가장 최근 설정 기준)
WITH latest AS (
  SELECT payload,
         ROW_NUMBER() OVER (PARTITION BY device_id ORDER BY occurred_at DESC) AS rn
  FROM events
  WHERE type = 'SETTINGS_CHANGED'
)
SELECT
  ROUND(AVG(JSON_EXTRACT(payload, '$.studyTimer') = true) * 100) AS study_timer_on_pct,
  ROUND(AVG(JSON_EXTRACT(payload, '$.stretch')    = true) * 100) AS stretch_on_pct,
  ROUND(AVG(JSON_EXTRACT(payload, '$.awayPause')  = true) * 100) AS away_pause_on_pct,
  ROUND(AVG(JSON_EXTRACT(payload, '$.overlay')    = true) * 100) AS overlay_on_pct
FROM latest
WHERE rn = 1;

-- 5) 전체 사용자의 시간대별 정자세 유지율 (취약 시간대 파악)
SELECT kst_hour,
       ROUND(SUM(duration_sec) / 3600, 1)                                            AS usage_hours,
       ROUND((SUM(duration_sec) - SUM(bad_duration_sec)) / SUM(duration_sec) * 100) AS keep_rate
FROM events
WHERE type = 'USAGE_SEGMENT' AND duration_sec > 0
  AND kst_date >= CURDATE() - INTERVAL 7 DAY
GROUP BY kst_hour
ORDER BY kst_hour;
```

- 4번은 설정을 한 번도 바꾸지 않은 기기가 빠진다. 온보딩 완료 시 기본값으로 `SETTINGS_CHANGED`를 1회 보내도록 합의하면 해결된다 (14장).
- 5번의 `CURDATE()`는 DB 세션 시간대가 KST로 설정되어 있어야 정확하다.

---

## 13. 개발 단계

| 단계 | 작업 | 완료 기준 |
| --- | --- | --- |
| **0. 준비·합의** | 14장 확인 사항 회신, AI 레포의 `validate.ts`·`summary.ts`·`*.test.ts`·`score.ts` 분석, 옮길 규칙 목록 작성 | 핵심 확인 사항(1~4번) 확정 |
| **1. 뼈대** | 4-3 설정으로 프로젝트 생성, 프로필 분리, JPA·시간대 설정(6-3, 6-4), Flyway V1 스키마, `EventEntity`, 에러 포맷, `TraceIdFilter`, `DeviceIdFilter`, springdoc 설정 | 서버가 MySQL에 연결되어 실행되고, 잘못된 헤더 요청 시 400 `DEVICE_ID_INVALID` + traceId 응답 |
| **2. 수집 API** | 최상위 구조 검증, 이벤트별 검증기, `EventRepository.insertIgnore` + `EventService`, `BodySizeFilter` | 중복 재전송 시 `duplicated`로 집계, 금지 필드 `rejected` |
| **3. 조회 API (기본)** | `date` 파라미터, `day`, `week`, `hourly`(24개), `hasData`, `comment` | FE 26-10-27 수정안의 응답과 일치 |
| **4. 조회 API (확장, FE 수요 확인 후)** | `yesterday`, `lastWeek`, `turtleNeckTimeline`, `distribution` | 분포 비율 합 100 테스트 통과 |
| **5. 보호 기능** | `RateLimitFilter`, IP HMAC 해시, 로그 마스킹, CORS | 한도 초과 시 429 + `retryAfter` |
| **6. 테스트 정리** | 계약 테스트, 10-3 경계 사례 체크리스트 전부 통과 | 예시 JSON 전부 일치 |
| **7. 로컬 연동** | 9-3 절차로 실제 클라이언트와 연결 | 앱 리포트 수치와 DB 집계 일치 |
| **8. 배포** | 서버 구성, HTTPS, 운영 프로필, 읽기 전용 계정, SSH 터널 | 배포 빌드 앱이 운영 서버로 연동 |
| **9. 전달** | 운영 SQL과 접속 가이드를 기획자에게 전달, OpenAPI 문서 공유 | 기획자가 직접 조회 가능 |
| **10. 확장 (합의 후)** | 정시 분할 검증 켜기, `mildDurationSec` 수신과 3단계 분포 | 클라이언트 배포 후 설정값으로 활성화 |

---

## 14. 확인이 필요한 사항

| # | 항목 | 현재 상태 / 백엔드 제안 | 확인 대상 | 영향 |
| --- | --- | --- | --- | --- |
| 1 | 이벤트 구조 | 기획서의 "세션 종료(초) + 정자세 회복" 대신 FE 초안의 `USAGE_SEGMENT` 채택 | 기획 | 수집·집계 전체 |
| 2 | 3단계 자세 분포 | AI는 나쁜 자세를 하나로 판정(ADR 0002). 살짝 숙임 추가 시 `badDurationSec` = 살짝 숙임 + 거북목, `mildDurationSec` 추가 제안. 살짝 숙임도 유지율에서 빠지는지 확인 | 기획, AI | 분포 차트, 유지율 정의 |
| 3 | `severity` 허용 값 | `score.ts` 확인 필요 | AI | 검증 규칙 |
| 4 | 개발 모드 mock 끄기 | 환경 변수만으로 실제 서버로 가는지 | AI | 로컬 연동 |
| 5 | 정시 분할 | `USAGE_SEGMENT`를 매 정시에서 끊어 주기 (현재 오차는 시간대당 최대 1분 수준) | AI | 시간대별 차트 정확도 |
| 6 | 거북목 횟수 문구 | `POSTURE_BAD`에 고개 숙임도 포함됨. 화면 문구 조정 여부 | 기획 | 리포트 화면 |
| 7 | 지난주 비교 기간 | 지난주 전체 vs 지난주 같은 요일까지 | 기획 | `lastWeek` 계산 |
| 8 | 코멘트 문구 | mood 기준은 mock과 같은 80/50, 문구 확정 필요 | 기획 | `comment` |
| 9 | 몰림 구간 기준 | 최소 몇 회부터 "몰림"인지, 동점 처리, 문구 생성 주체 (제안: 서버는 `peakHours`, 문구는 FE) | 기획, FE | `peakHours` |
| 10 | 시간대 최소 사용 시간 | 2~3분짜리 시간대도 막대로 보여줄지 | 기획 | `hourly` |
| 11 | 시간 표기 | 공통 규칙은 24시간제인데 시안에 "오후 8시" 표기 혼재 | 기획 | FE 표시 |
| 12 | 뽀모도로 카드 비교 | "어제 대비"인지 "이번 주 누적"인지 | 기획 | FE 표시 |
| 13 | 기본 설정값 이벤트 | 온보딩 완료 시 `SETTINGS_CHANGED` 1회 전송 | AI | 운영 SQL 4번 |
| 14 | 리포트 호출 위치 | main 프로세스인지 렌더러인지 | AI | CORS 설정 |
| 15 | 엔드포인트 이름 | 기획서 `GET /report` → `GET /api/reports/summary`로 통일 | 기획 | 문서 정합성 |
| 16 | 데이터 보관 기간 | 기간 제안 후 확정 | 기획 | 삭제 배치 |
| 17 | 배포 환경 | 클라우드, 도메인, 인증서 | 팀 | 배포 |
| 18 | ~~`hasData` 범위~~ | **확정 (10-27)**: 요청 날짜(`day`) 기준 | FE | 빈 상태 화면 |
| 19 | 확장 필드 필요 여부 | `yesterday`, `lastWeek`, `turtleNeckTimeline`, `distribution`이 아직 필요한지. 어제는 `date`로 재조회 가능, `lastWeek`는 주 단위 비교가 필요하면 추가 | FE | 7-3 확장 필드 |
| 20 | 미래 `date` 처리 | 제안: `200` + 빈 응답(`hasData: false`). 400이 필요하면 변경 | FE | 7-3 |
| 21 | 시 경계 구간 집계 | **확정 (10-27)**: `startedAt`의 시로 집계 (분할하지 않음) | FE | `hourly` |

**백엔드가 회신할 항목** (AI 레포 인수인계 문서 기준)

| 항목 | 회신 내용 |
| --- | --- |
| Rate Limit 수치 | 8-7 제안값 |
| IP 해시 방식 | HMAC-SHA256 + 서버 비밀키, 저장하지 않음 |
| 이번 주 정의 | 요청 날짜가 속한 주의 월요일 00:00(KST) ~ 요청 날짜 (오늘이면 mock과 동일) |
| 코멘트 mood 규칙 | mock과 동일 (80/50), 문구는 기획 확정 후 반영 |
| traceId 형식 | `req-` + 랜덤 8자리 16진수 |
| OpenAPI 문서 | 서버 코드에서 springdoc으로 자동 생성 |

---

## 15. 리스크

| 리스크 | 영향 | 대응 |
| --- | --- | --- |
| 서버와 mock의 검증 규칙 불일치 | 이벤트가 조용히 유실 | 참고 구현 이식 + 예시 JSON 계약 테스트 |
| `INSERT IGNORE`가 중복 외 오류까지 무시 | 잘못된 값 저장 | 저장 전 전수 검증, Testcontainers로 확인 |
| 확장 검증 규칙을 클라이언트보다 먼저 적용 | 정상 이벤트 전부 거절 | 설정값으로 분리, 클라이언트 배포 후 활성화 |
| Rate Limit이 너무 엄격 | 오프라인 후 동기화 지연 | 일괄 동기화를 수용하는 한도, 설정값으로 조정 |
| 공유 IP 환경 | 여러 사용자가 함께 429 | IP 기준 한도를 기기 기준보다 크게 |
| 시간대 처리 실수 | 날짜·시간대 집계 오류 | 변환 로직을 `KstTimeUtils` 한 곳에 모으고, JVM·DB 연결·Hibernate 시간대를 모두 고정, 경계 테스트 |
| 엔티티와 실제 스키마 불일치 (`ddl-auto: none`이라 실행 시 검출 안 됨) | 조회·매핑 오류 | Testcontainers 통합 테스트에서 Flyway 스키마 위에 엔티티 조회까지 확인 |
| 실수로 JPA `save()` 사용 | 중복 시 배치 전체 실패 또는 덮어쓰기 | 저장은 `insertIgnore`만 쓰도록 코드 리뷰에서 확인 |
| springdoc과 Boot 버전 불일치 | 실행 오류, Swagger UI 404 | springdoc 지원이 확인된 Boot 버전 사용 (4-2) |
| 기기 ID 유실 (재설치 등) | 기록 끊김 | 기획에서 감수하기로 함, 향후 로그인으로 해소 |
| 기획자 DB 직접 접근 | 실수로 데이터 변경 | 읽기 전용 계정, SSH 터널, 외부 포트 차단 |