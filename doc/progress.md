# 펴부기 백엔드 진행 현황

| 항목 | 내용 |
| --- | --- |
| 기준일 | 2026-10-28 |
| 기준 커밋 | `fbfc1c6` (main) |
| 설계 문서 | [`doc/plan.md`](plan.md) (v3). 이 문서는 **무엇이 끝났고 무엇이 남았는지**만 정리하고, 규칙과 설계 이유는 plan.md 장 번호로 연결한다 |

---

## 1. 한눈에 보기

| 단계 (plan 13장) | 상태 | 비고 |
| --- | --- | --- |
| 0. 준비·합의 | ✅ 완료 | AI 레포 mock(`validate.ts`, `summary.ts`) 분석, 검증 순서 확정 |
| 1. 뼈대 | ✅ 완료 | 환경 설정, 시간대 고정, 스키마, 에러 형식, 필터 |
| 2. 수집 API `POST /api/events` | ✅ 완료 | 검증기, 멱등 저장, 계약 테스트 |
| 3. 조회 API (기본) `GET /api/reports/summary` | ✅ 완료 | `date`, `day`, `week`, `hourly`, `hasData`, `comment` |
| 4. 조회 API (확장) | ✅ 완료 | `yesterday`, `lastWeek`, `turtleNeckTimeline`, `distribution` |
| 5. 보호 기능 | ✅ 완료 | Rate Limit, IP HMAC 해시, 요청 로그. CORS는 불필요해서 제외 |
| 6. 테스트 정리 | ✅ 사실상 완료 | 테스트 157개, plan 10-3 경계 사례 32/32 |
| 7. 로컬 연동 | 🔶 기본 확인 | AI 앱 → 서버 저장·집계 일치 확인 (3-6). 자정 경계·재전송은 남음 |
| 8. 배포 | ⏳ 진행 중 | Railway로 결정 |
| 9. 전달 | ⏸ 대기 | 배포 후 |
| 10. 확장 (합의 후) | ⏸ 대기 | AI·기획 합의 필요 |

---

## 2. 처음 받았을 때 실행 방법

**필요한 것**: JDK 21, Docker Desktop (로컬 DB와 테스트 둘 다 Docker 사용)

```bash
./gradlew test        # 전체 테스트 (Testcontainers가 MySQL 8.4 컨테이너를 띄움, 약 1분)
./gradlew bootRun     # local 프로필로 서버 실행, compose.yaml의 MySQL이 자동으로 뜸
```

| 항목 | 값 |
| --- | --- |
| 서버 | `http://localhost:8080` |
| Swagger UI | `http://localhost:8080/swagger-ui/index.html` |
| 로컬 DB 접속 (IntelliJ Database 탭 등) | `127.0.0.1:13306` / DB `pyeobugi` / `pyeobugi` / `pyeobugi-local` |

- 서버나 Docker Desktop을 꺼도 DB 데이터는 남는다. `docker compose down`이나 컨테이너 삭제를 하면 지워진다.
- 기본 프로필은 `local`이다. 운영(`prod`)은 환경 변수 `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`, `IP_HASH_SECRET`(32자 이상)이 필요하다 (plan 11-1).

---

## 3. 완료한 작업

### 3-1. 환경과 뼈대 (1단계)

| 작업 | 내용 | plan |
| --- | --- | --- |
| 빌드 | Spring Boot 4.0.9, Java 21, springdoc 3.0.3 (3.1부터는 Boot 4.1 기준이라 3.0.x 고정) | 4장 |
| DB | MySQL 8.4 고정 (`compose.yaml`, Testcontainers 둘 다), 로컬 포트 13306 | 6-4 |
| 시간대 | JVM·테스트 JVM·JDBC 세션·Hibernate 모두 `Asia/Seoul`. 테스트로 확인 | 6-3 |
| 스키마 | Flyway `V1__create_events.sql`, `EventEntity` | 6-1, 6-4 |
| 시각 처리 | `KstClock`(테스트에서 시계 고정), `KstTimeUtils`(`+09:00`·초 단위만 허용하는 엄격한 파싱) | 6-3 |
| 에러 형식 | `{ code, message, traceId, retryAfter? }`, 컨트롤러 예외는 `GlobalExceptionHandler`, 필터 오류는 `ErrorResponseWriter` | 7-1 |
| 필터 | `TraceIdFilter` → `AccessLogFilter` → `DeviceIdFilter` → `RateLimitFilter` → `BodySizeFilter` (순서 상수 `FilterOrders`) | 8-1 |

### 3-2. 수집 API (2단계)

- 최상위 구조 오류는 400, 이벤트 101개 이상이나 본문 64KB 초과는 413이다.
- 이벤트 하나에 문제가 있으면 배치 전체를 거절하지 않고 그 이벤트만 `rejected`로 응답한다.
- **검증 순서는 mock `validate.ts`와 같다.** FE 요청(10-27)에 따라 `FORBIDDEN_FIELD`만 맨 앞으로 올렸다 (plan 8-2 표).
- 저장은 이벤트마다 네이티브 `INSERT IGNORE`를 쓴다. 반환값 1이면 `accepted`, 0이면 `duplicated`이고, 한 트랜잭션에서 처리한다 (plan 8-3).
- **mock과 일부러 다르게 한 점** (plan 8-2):
  - 시각은 `+09:00`·초 단위만 받는다.
  - 기기 ID는 소문자로 바꿔 저장한다.
  - 금지 필드가 다른 오류와 같이 있으면 `FORBIDDEN_FIELD`가 우선한다.

### 3-3. 조회 API (3·4단계)

- 응답은 FE 10-27 수정안(`date`, `day`, `week`, `hourly`, `hasData`, `comment`)에 확장 필드 4개를 **추가**한 형태다 (plan 7-3).
- 유지율은 합계로 계산하고 마지막에 한 번만 반올림한다. 주간 값도 일별 평균이 아니라 합계로 계산한다.
- `hourly`는 항상 24개다. 시 경계를 걸친 1분 구간은 `startedAt`의 시로 집계한다 (FE와 합의).
- 미래 `date`는 400 대신 빈 응답을 준다 (FE에 제안한 상태, 14장 #20).

### 3-4. 보호 기능 (5단계)

- **Rate Limit**: 기기 ID 기준 분당 120회(순간 60회), IP 해시 기준 분당 1,200회. 수치는 `application.yml`의 `pyeobugi.rate-limit`에서 조정한다 (plan 8-7).
- **IP 해시**: HMAC-SHA256 + 서버 비밀키. IP는 메모리에서만 쓰고 DB와 로그에는 남기지 않는다.
- **요청 로그**: 메서드, 경로, 상태 코드, 처리 시간, 기기 ID 앞 8자리만 남긴다. 본문, 쿼리 문자열, IP는 남기지 않는다 (plan 8-8).

### 3-5. 테스트

| 종류 | 대표 파일 | 확인 내용 |
| --- | --- | --- |
| 단위 | `EventValidatorTest`, `KstTimeUtilsTest`, `RateLimitFilterTest` 등 | 검증 순서, 시각 형식, 반올림, 경계값 |
| 통합 (실제 MySQL) | `EventServiceTest`, `EventApiIntegrationTest`, `ReportApiIntegrationTest` | `INSERT IGNORE` 1/0, 중복, 주·시 경계, 빈 상태 |
| 계약 | `EventsContractTest`, `ReportsContractTest` | AI 레포 `docs/api/examples/*.json`을 넣었을 때 mock과 같은 응답 |
| 실제 서버 | `SkeletonIntegrationTest` | 랜덤 포트 Tomcat에서 필터 체인 동작 |

### 3-6. 로컬 연동 (7단계, 10-27)

AI 레포(`main`, `c5b4d86`)를 `MAIN_VITE_API_BASE_URL=http://localhost:8080 npm run dev`로 실행해 실제 앱 이벤트를 받았다.

| 확인 항목 | 결과 |
| --- | --- |
| 수집 | 이벤트 10개 (`SESSION_START`, `CALIBRATION_COMPLETED`, `USAGE_SEGMENT` 6, `POSTURE_BAD` 2) 모두 `accepted`, `rejected` 0 |
| 전송 지연 | `occurred_at` → `received_at` 최대 8초 (앱 큐가 10초 주기로 전송) |
| 집계 | DB 원본으로 직접 계산한 값과 리포트 일치: 사용 107초, 나쁜 자세 11초 → 유지율 90, 거북목 2회 |
| 날짜 전환 | 자정 이후 조회 시 전날 값이 `yesterday`로 이동, `week`는 유지 |

남은 확인:

- 자정을 걸친 사용 구간 분할 (앱이 23:57에 종료돼 확인 못 함)
- 서버 중단 후 앱 큐 재전송과 `duplicated` 처리
- 여러 시간대에 걸친 `hourly`
- 앱 리포트 화면: 앱이 아직 `today`를 읽어 서버 응답(`day`)을 표시하지 못한다. 서버 문제가 아니라 #22
- 처음 실행할 때 `Electron uninstall` 에러가 나면 Electron 바이너리가 다운로드되지 않은 것이다. AI 레포에서 `node node_modules/electron/install.js`로 해결

---

## 4. 남은 작업

### 4-1. 바로 할 수 있는 것

| 작업 | 방법 | 비고 |
| --- | --- | --- |
| **7. 로컬 연동 (나머지)** | 3-6 "남은 확인" 항목. 자정 경계는 23:58쯤 앱을 켜서 00:01 이후까지 사용 | 기본 연동은 완료 |
| 운영 SQL 검증 | plan 12장 SQL을 로컬 DB에서 실행해 보기 | 9단계 전달 전에 |
| 데이터 보관 배치 | 보관 기간 확정 후 오래된 이벤트 삭제 | 기간 미정 (#16) |

### 4-2. 다른 파트 회신이 필요한 것 (plan 14장)

| # | 항목 | 현재 서버 동작 | 확인 대상 |
| --- | --- | --- | --- |
| 19 | 확장 필드 정의 공유 | 구현 완료. plan 7-3 "확장 필드" 표를 FE에 전달해야 함 | FE |
| 20 | 미래 `date` 처리 | 200 + 빈 응답 (제안값) | FE |
| 22 | mock 갱신 | mock이 10-27 이전 기준(`today`, 전체 기간 `hasData`, 느슨한 시각 형식). 갱신되면 `src/test/resources/contract/` 예시도 다시 복사 | AI, FE |
| 7 | `lastWeek` 비교 기간 | 지난주 같은 요일까지 (제안값, `ReportService` 한 줄로 변경 가능) | 기획 |
| 8 | 코멘트 문구 | mock 문구 그대로. 과거 날짜를 조회해도 "오늘 잘했어요"가 나옴 | 기획 |
| 9 | 몰림 구간(`peakHours`) 기준 | 최다 시간대, 최소 2회, 동점은 모두 포함 (`PeakHours.MIN_COUNT`) | 기획 |
| 10 | 시간대 최소 사용 시간 | 기준 없음 (1초라도 있으면 표시) | 기획 |
| 16 | 데이터 보관 기간 | 미정 | 기획 |
| 17 | 배포 환경 | Railway로 결정. 커스텀 도메인 사용 여부는 미정 (Railway 기본 도메인도 HTTPS 제공) | 팀 |
| 2, 5 | 3단계 자세 분포, 정시 분할 | 미구현 (10단계) | AI, 기획 |

### 4-3. 이후 단계

- **8. 배포 (Railway)**: Railway가 HTTPS를 처리하므로 plan 11-2의 Nginx는 쓰지 않는다. 구성은 Spring Boot 서비스(`Dockerfile`)와 MySQL 서비스다.
  - 운영 프로필에 위 환경 변수를 넣는다. DB 값은 Railway MySQL 변수를 참조한다.
  - 읽기 전용 DB 계정을 만든다. 기획자 접속은 SSH 터널 대신 Railway MySQL의 TCP Proxy를 쓴다.
- **9. 전달**: 운영 SQL과 접속 가이드를 기획자에게, OpenAPI 문서를 FE에 공유한다.
- **10. 확장**: 클라이언트 배포 후 설정값(feature flag)으로 켠다. 먼저 켜면 정상 이벤트가 전부 거절된다 (plan 8-2).

---

## 5. 같이 작업할 때 알아 둘 점

| 주의 | 이유 |
| --- | --- |
| **`V1__create_events.sql`은 수정하지 말고 `V2__...sql`을 새로 추가** | 이미 적용된 마이그레이션을 고치면 Flyway가 체크섬 불일치로 서버 실행을 막는다 |
| **저장에 JPA `save()`를 쓰지 않고 `EventRepository.insertIgnore`만 사용** | `save()`는 중복 시 배치 전체 롤백이나 덮어쓰기가 생긴다 (plan 6-4) |
| **현재 시각은 `LocalDateTime.now()` 대신 `KstClock`** | 테스트에서 시계를 고정해야 시각이 들어간 테스트가 재현된다 |
| **KST 변환·파싱은 `KstTimeUtils`에서만** | 시간대 처리를 한 곳에 모아 실수를 줄인다 |
| **검증 규칙을 바꾸면 mock(`validate.ts`)과 같은지 확인** | 판정이 다르면 클라이언트가 이벤트를 조용히 버린다 (plan 9-2) |
| `@WebMvcTest`를 새로 쓸 때 | 필터가 같이 뜬다. 필요 없으면 `GlobalExceptionHandlerTest`처럼 `excludeFilters`로 빼거나, 필터가 쓰는 빈을 `@Import` |
| 테스트끼리 DB를 공유함 | 통합 테스트는 테스트마다 `UUID.randomUUID()` 기기 ID를 써서 데이터가 섞이지 않게 한다 |
| 비밀값 | `application-local.yml`의 키는 개발용이다. 운영 키는 환경 변수로만 넣고 Git에 올리지 않는다 |

**커밋 메시지 형식**: `feat:`, `test:`, `docs:`, `chore:` + 한국어 요약, 본문은 `-` 목록 (예: `git log --oneline`).
