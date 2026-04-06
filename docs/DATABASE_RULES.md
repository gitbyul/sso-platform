# 데이터베이스 규칙 & 인덱스 가이드 (PostgreSQL 16 기준)

이 문서는 기존 인덱스 가이드 초안의 모호/오류 부분을 보정한 **실행 가능한 규칙**이다.
프로젝트 표준 스키마는 [`AGENTS.md`](../AGENTS.md) 및 `.cursor/rules/agent-module-*.mdc`의 바운디드 컨텍스트 정의에 맞춘다. (`shared`, `tenant`, `identity`, `client`, `session`, `key`, `audit`, `federation` 등 — 스키마 추가 시 Flyway와 본 문서의 모니터링 절을 함께 갱신한다.)

**문서 진입점:** [`README.md`](../README.md) · [`DEVELOPMENT_PLAN.md`](./DEVELOPMENT_PLAN.md) · [`AGENTS.md`](../AGENTS.md). DDL은 `sso-bootstrap/src/main/resources/db/migration/`에 두며, 버전·파일 규칙은 `.cursor/rules/agent-flyway-versioning.mdc`, **객체 이름·인덱스·운영 점검**은 본 문서가 기준이다.

---

## 0) 산업 일반 가이드 (참고)

다음은 PostgreSQL 생태계와 SQL 스타일 문서에서 **반복적으로 인용되는 관행**이다. 법적 표준이 아니라, 신규 설계 시 설득력 있는 근거로 삼을 수 있는 수준의 합의다.

| 원칙 | 설명 | 참고 |
|------|------|------|
| 식별자는 가급적 **소문자** | 따옴표 없이 생성하면 PostgreSQL은 소문자로 귀결된다. 혼용 시 대소문자 혼란이 생긴다. | [PostgreSQL — Lexical Structure / Identifiers](https://www.postgresql.org/docs/current/sql-syntax-lexical.html#SQL-SYNTAX-IDENTIFIERS) |
| **snake_case** | 단어 구분은 밑줄. 가독성·검색·ORM 매핑에 유리하다. | [SQL Style Guide](https://www.sqlstyle.guide/) |
| 테이블은 보통 **복수형** 명사 | 행의 집합을 담는다는 의미 (`users`, `orders`). (팀 전체가 단수로 통일할 수는 있으나, 본 문서는 복수형을 채택한다.) | 위 스타일 가이드 등 |
| **스키마로 네임스페이스** | 객체 종류(`tb_`)를 테이블명에 붙이기보다 `sales.orders`처럼 스키마로 경계를 둔다. | [PostgreSQL — Schemas](https://www.postgresql.org/docs/current/ddl-schemas.html) |
| **`tb_` / `tbl_` 접두어** | 객체 타입을 이름으로 표시하는 헝가리안식 접두어는 **현대적 논의에서 필수·권장 사항으로 널리 쓰이지 않는다.** 레거시 표준이 아닌 한 **사용하지 않는 것**을 본 문서는 채택한다. | 커뮤니티 합의·상기 스타일 가이드 방향 |

**본 문서 §2**는 위 원칙을 **이 프로젝트 DDL에 구체화**한 규칙이다. Flyway 스크립트·리뷰 시 §0과 §2를 함께 본다.

---

## 1) 기존 초안에서 수정된 핵심 포인트

1. `public` 스키마 고정 모니터링은 부적합  
   - 본 프로젝트는 `public`이 아닌 복수 스키마를 사용하므로, 모니터링 쿼리는 대상 스키마를 명시해야 한다.
2. PK/FK/UK는 "인덱스"보다 "제약조건" 우선으로 표현 필요  
   - PostgreSQL에서 PK/UK는 내부 인덱스가 생성되지만, **명명 주체는 제약조건**이다.
3. 일부 SQL 예시 문법 오류 보정 필요  
   - 누락된 쉼표, 불필요한 쉼표, 잘못된 컬럼 참조 등이 있어 그대로 실행 불가.
4. `pg_stat_statements`에서 인덱스명을 `LIKE`로 찾는 방식은 논리적으로 부정확  
   - 쿼리는 인덱스명을 직접 포함하지 않는다. 느린 쿼리 추출 후 `EXPLAIN`으로 판단해야 한다.
5. PostgreSQL 특화 인덱스 접두사 규칙 정리 필요  
   - B-Tree/GIN/BRIN 등 타입 접두사와 일반 `idx_` 규칙이 충돌하지 않도록 통합해야 한다.

---

## 2) 명명 규칙 (프로젝트 최종)

§0의 산업 관행을 따르며, 아래는 **강제 규칙**이다. `.cursor/rules/agent-flyway-versioning.mdc`와 Flyway 리뷰는 **본 절(§2) 준수**를 전제로 한다.

### 2.1 공통 (식별자 전반)

- 소문자 + `snake_case`만 사용한다.
- 의미가 명확한 이름을 쓴다 (테이블, 컬럼, 제약조건 목적이 드러나야 함).
- PostgreSQL 식별자 **최대 63바이트** 제한을 넘기지 않으며, **권장 50자 이내**로 짧게 유지한다.
- 과도한 축약 금지 (`usr`, `eml` 등).
- **테이블·뷰 이름에 `tb_`, `tbl_` 등 객체 종류 접두어를 붙이지 않는다.** (§0)

### 2.2 테이블·뷰

- **스키마:** 바운디드 컨텍스트별로 분리한다 (`tenant`, `identity`, …). 테이블명에 스키마 역할을 중복해 붙이지 않는다. (`tenant.tenants` O, 테이블명 `tenant_tenants`로 스키마를 흉내 내기 X)
- **테이블 이름:** **복수형** `snake_case` (`tenants`, `outbox_events`, `oauth_clients`).
- **뷰:** 접두사 `v_`를 허용한다 (`v_active_tenants`). (뷰 도입 시 팀 합의 하에 일관 적용)

### 2.3 컬럼

- `snake_case`, 의미 단위로 읽히게 한다 (`tenant_id`, `email_verified`, `created_at`).
- 시각: `_at` (`TIMESTAMPTZ`), 불리언은 `is_` / `_flag` 등 **한 가지 스타일로 통일**한다.
- 외래 참조 컬럼은 가급적 `{참조테이블 단수}_id` 형태 (`tenant_id` → `tenants.tenant_id` 등 팀 규약에 맞춤).

### 2.4 제약조건 명명

| 타입 | 형식 | 예시 |
|---|---|---|
| Primary Key | `pk_{table}` | `pk_tenants` |
| Foreign Key | `fk_{table}_{ref_table}_{column}` | `fk_tenant_settings_tenants_tenant_id` |
| Unique | `uk_{table}_{columns}` | `uk_tenants_domain` |
| Check | `chk_{table}_{rule}` | `chk_tenants_status_valid` |

`{table}`에는 **실제 테이블 이름 전체**를 넣는다 (접두어 `tb_` 없음).

### 2.5 인덱스 명명

| 타입 | 형식 | 예시 |
|---|---|---|
| 일반 B-Tree | `idx_{table}_{columns}` | `idx_users_tenant_id_email` |
| Partial Index | `idx_{table}_{columns}_{predicate}` | `idx_outbox_created_at_unpublished` |
| GIN (FTS/JSONB) | `gin_{table}_{columns}` | `gin_audit_events_metadata` |
| BRIN | `brin_{table}_{column}` | `brin_audit_events_created_at` |

팀 표준으로 `idx_`를 기본으로 쓰고, GIN/BRIN처럼 타입이 중요한 경우만 `gin_`/`brin_` 접두어를 허용한다.

---

## 3) 인덱스 생성 우선순위 (보정)

| 우선순위 | 항목 | 설명 |
|---|---|---|
| 1 | PK/UK 제약조건 | 데이터 무결성의 기본. 자동 인덱스 생성됨 |
| 2 | FK 참조 컬럼 인덱스 | FK 컬럼 인덱스는 자동 생성되지 않으므로 필요 시 명시 생성 |
| 3 | 자주 쓰는 WHERE/JOIN 단일 컬럼 | 선택도 높은 컬럼 우선 |
| 4 | 복합 인덱스 | 실제 쿼리 조건 순서 기준으로 설계 |
| 5 | Partial 인덱스 | 고정 조건(예: `published=false`) 최적화 |
| 6 | INCLUDE(커버링) 인덱스 | 조회 컬럼 포함으로 heap 접근 감소 |
| 7 | 특수 인덱스(GIN/BRIN) | JSONB/FTS/대용량 시계열 등 명확한 근거가 있을 때 |

---

## 4) PostgreSQL 특화 가이드

- B-Tree: 기본 선택
- Hash: 특수 케이스 외 권장하지 않음 (운영 표준은 B-Tree)
- BRIN: append-only, 대용량 시간축 테이블에만 적용
- GIN: JSONB 검색, Full Text Search
- Bloom: 확장 설치 필요. 기본 표준에서 제외(특정 성능 실험 시에만)

---

## 5) 프로젝트 스키마 기준 모니터링 SQL (수정본)

**대상 스키마** (AGENTS/rules 기준, 운영 DB에 존재하는 스키마만 결과에 나타남):

`shared`, `tenant`, `identity`, `client`, `session`, `key`, `audit`, `federation`

스키마를 추가·제거한 경우 아래 `IN (...)` 목록을 동기화한다.

### 5.1 인덱스 사용률

```sql
SELECT
    schemaname,
    relname AS table_name,
    indexrelname AS index_name,
    idx_scan,
    idx_tup_read,
    idx_tup_fetch,
    CASE
        WHEN idx_scan = 0 THEN 'UNUSED'
        WHEN idx_scan < 100 THEN 'LOW_USAGE'
        WHEN idx_scan < 1000 THEN 'MEDIUM_USAGE'
        ELSE 'HIGH_USAGE'
    END AS usage_level,
    pg_size_pretty(pg_relation_size(indexrelid)) AS index_size
FROM pg_stat_user_indexes
WHERE schemaname IN (
    'shared', 'tenant', 'identity', 'client',
    'session', 'key', 'audit', 'federation'
)
ORDER BY idx_scan DESC, pg_relation_size(indexrelid) DESC;
```

### 5.2 미사용 보조 인덱스 (팀 명명 규칙)

Primary Key / Unique 제약이 만든 인덱스는 제외하고, **`idx_` / `gin_` / `brin_` 접두어**를 쓴 인덱스만 본다.

```sql
SELECT
    schemaname,
    relname AS table_name,
    indexrelname AS index_name,
    idx_scan,
    idx_tup_read,
    idx_tup_fetch,
    pg_size_pretty(pg_relation_size(indexrelid)) AS index_size
FROM pg_stat_user_indexes
WHERE idx_scan = 0
  AND schemaname IN (
      'shared', 'tenant', 'identity', 'client',
      'session', 'key', 'audit', 'federation'
  )
  AND (
      indexrelname LIKE 'idx_%'
      OR indexrelname LIKE 'gin_%'
      OR indexrelname LIKE 'brin_%'
  )
ORDER BY pg_relation_size(indexrelid) DESC;
```

### 5.3 인덱스 크기

```sql
SELECT
    schemaname,
    relname AS table_name,
    indexrelname AS index_name,
    pg_size_pretty(pg_relation_size(indexrelid)) AS index_size,
    pg_size_pretty(pg_relation_size(relid)) AS table_size,
    ROUND(
        (pg_relation_size(indexrelid)::numeric / NULLIF(pg_relation_size(relid), 0)::numeric) * 100,
        2
    ) AS size_ratio_percent
FROM pg_stat_user_indexes
WHERE schemaname IN (
    'shared', 'tenant', 'identity', 'client',
    'session', 'key', 'audit', 'federation'
)
ORDER BY pg_relation_size(indexrelid) DESC;
```

### 5.4 느린 쿼리 확인 (`pg_stat_statements`)

```sql
-- 사전: CREATE EXTENSION IF NOT EXISTS pg_stat_statements;
SELECT
    queryid,
    calls,
    total_exec_time,
    mean_exec_time,
    rows,
    query
FROM pg_stat_statements
ORDER BY mean_exec_time DESC
LIMIT 30;
```

> 위 결과에서 후보 쿼리를 뽑아 `EXPLAIN (ANALYZE, BUFFERS)`로 실제 인덱스 사용을 확인한다.

### 5.5 중복 인덱스(동일 정의) 탐지

```sql
WITH idx AS (
    SELECT
        schemaname,
        tablename,
        indexname,
        regexp_replace(indexdef, '^CREATE( UNIQUE)? INDEX [^ ]+ ON ', 'CREATE INDEX ON ') AS normalized_def
    FROM pg_indexes
    WHERE schemaname IN (
        'shared', 'tenant', 'identity', 'client',
        'session', 'key', 'audit', 'federation'
    )
)
SELECT
    a.schemaname,
    a.tablename,
    a.indexname AS index1,
    b.indexname AS index2,
    a.normalized_def
FROM idx a
JOIN idx b
  ON a.schemaname = b.schemaname
 AND a.tablename = b.tablename
 AND a.normalized_def = b.normalized_def
 AND a.indexname < b.indexname
ORDER BY a.schemaname, a.tablename, a.indexname;
```

---

## 6) 인덱스 생명주기 (운영 규칙)

- `temp_`, `exp_` 인덱스는 운영 반영 전 제거 원칙
- 임시/실험 인덱스는 티켓 번호와 만료일을 코멘트로 남길 것
- 30일 이상 `idx_scan=0`인 인덱스는 제거 후보로 분류
- 제거 전 반드시:
  1. 최근 슬로우 쿼리 상위 목록 확인
  2. 제거 대상 인덱스 없는 상태의 `EXPLAIN` 비교
  3. 롤백 계획(재생성 SQL) 준비

---

## 7) Flyway 적용 규칙 연계

- 모든 스키마 변경은 `sso-bootstrap/src/main/resources/db/migration/`에서만 관리
- 이미 적용된 마이그레이션 파일 수정 금지
- 인덱스 생성은 반드시 명시적 이름 사용 (`idx_*`, `gin_*`, `brin_*`)
- **테이블·컬럼·제약조건 명명은 §2 및 §0 정렬 원칙 준수**
- **`public` 스키마:** 앱 객체는 전용 스키마에 두고, `public`에 대한 `CREATE`는 `REVOKE … FROM PUBLIC`로 막는다(`V0.3.0__harden_public_schema_permissions.sql`). `public` 스키마 자체는 삭제하지 않는다(본 문서 도입부·§0와 동일 취지).
- 대용량 테이블에 `CREATE INDEX CONCURRENTLY`가 필요하면 별도 마이그레이션 전략 수립
  - (Flyway 트랜잭션 정책과 충돌 가능하므로 사전 검토 필수)

---

## 8) 좋은/나쁜 예시 (보정)

```sql
-- 좋은 예시 (복수 테이블명, tb_ 없음, 스키마 분리)
CREATE TABLE IF NOT EXISTS tenant.tenants (...);
CREATE UNIQUE INDEX uk_tenants_domain ON tenant.tenants (domain);
CREATE INDEX idx_tenant_settings_tenant_id ON tenant.tenant_settings (tenant_id);
CREATE INDEX idx_outbox_created_at_unpublished
    ON shared.outbox_events (created_at)
    WHERE published = false;

-- 나쁜 예시
CREATE TABLE tenant.tb_tenants (...);                 -- tb_ 접두어 (본 문서 §2.1 비준수)
CREATE INDEX email ON tenant.users (email);          -- 접두사/의미 없음
CREATE INDEX idx_usr_eml ON tenant.users (email);    -- 과도한 축약
CREATE INDEX users_email_idx ON tenant.users (email); -- 팀 표준 접두사·순서 불일치
```
