# SSO Platform 개발 계획서

> 대상 독자: 개발자 (프로젝트 진행 상황 추적 및 의사결정 근거 참조)
> 최종 업데이트: 2026-04-03 (AGENT_SPEC.md 보안 명세와 동기화)

---

## 1. 프로젝트 개요

### 목적

엔터프라이즈 멀티테넌트 환경을 위한 Self-Hosted SSO(Single Sign-On) 플랫폼 구축.
OAuth 2.0 / OIDC 표준을 완전 준수하며, 테넌트별 독립적인 인증·인가 정책과 외부 IdP 연동(SAML 2.0, OIDC Federation)을 지원한다.

### 대상 환경

- 멀티테넌트 B2B SaaS / 엔터프라이즈 내부 플랫폼
- 테넌트별 독립 정책 (패스워드 정책, MFA 필수 여부, 세션 타임아웃)
- 금융보안원·GDPR·ISO 27001 규정 준수 대응

### 기술 스택 확정

| 영역 | 기술 |
|---|---|
| 언어 / 프레임워크 | Java 25 + Spring Boot 4.0.5 |
| 빌드 | Gradle 9.4.1 (Kotlin DSL), 멀티모듈 |
| Authorization Server | Spring Authorization Server |
| 아키텍처 | CQRS + DDD, 바운디드 컨텍스트 멀티모듈 |
| 패스워드 해싱 | Argon2id (DelegatingPasswordEncoder) |
| DB 마이그레이션 | Flyway |
| 테넌트 식별 | 서브도메인 Primary. 운영: `X-Tenant-ID` Fallback 금지. M2M: 토큰 `tenant_id` 클레임. JWT–`TenantContextHolder` 바인딩 검증 (`TenantJwtValidationFilter`) |
| 세션 저장 | Redis (Spring Session) |
| 이벤트 스트림 | PostgreSQL Outbox + Redis Streams → Kafka (Phase 2) |
| 키 관리 | HashiCorp Vault (Transit Engine) |
| 메트릭 | Micrometer + Prometheus + Grafana |
| 분산 추적 | OpenTelemetry + Grafana Tempo |
| 로그 | Logback JSON + Grafana Loki |
| 감사 로그 | PostgreSQL `audit` 스키마 (불변, 월별 파티셔닝) |

**문서 관계 (진입점):**

| 문서 | 역할 |
|------|------|
| 본 문서 (`DEVELOPMENT_PLAN.md`) | 로드맵, ADR, Phase, 운영 관점 |
| [`AGENT_SPEC.md`](./AGENT_SPEC.md) | 구현 강제 규칙·보안·모듈·Flyway 파일 위치 |
| [`DATABASE_RULES.md`](./DATABASE_RULES.md) | PostgreSQL 명명(§0 산업 관행·§2 강제), 인덱스·제약조건, 모니터링 SQL |

- 구현·보안·코드 구조가 상충하면 **`AGENT_SPEC.md`**를 따른다.
- DDL·인덱스·제약조건 이름·운영 점검 쿼리는 **`DATABASE_RULES.md`** §0·§2를 따른다. (Flyway 스크립트 작성 시 필수 참조)

---

## 2. 아키텍처 결정 기록 (ADR)

### ADR-001: CQRS + DDD 멀티모듈

**결정:** 각 바운디드 컨텍스트를 독립 Gradle 모듈로 분리하고, 모듈 내부는 `command` / `query` 패키지로 CQRS를 적용한다.

**이유:**
- 인증·인가·테넌트·감사는 서로 다른 변경 주기와 팀 소유권을 가짐
- Command(쓰기)와 Query(읽기)의 성능 요구사항이 다름 (Query는 복잡 JOIN, Command는 집계 무결성)
- 미래 MSA 전환 시 모듈 경계가 서비스 경계가 됨

**제약:**
- 모듈 간 직접 도메인 객체 참조 금지. 공유 데이터는 `sso-shared-kernel`의 ID·DTO·이벤트 인터페이스로만 교환
- Command 핸들러는 반환값을 ID 또는 void로 제한 (조회 로직 혼입 금지)
- Query 핸들러는 상태 변경 금지 (JPA Dirty Checking 차단 — `@Transactional(readOnly=true)`)

---

### ADR-002: Spring Authorization Server 선택

**결정:** OAuth 2.0 / OIDC Authorization Server 구현에 Spring Authorization Server를 사용한다.

**이유:**
- Spring Security 생태계와 완전 통합 (SecurityFilterChain, UserDetailsService)
- RFC 표준 엔드포인트 자동 제공 (`/oauth2/authorize`, `/oauth2/token`, `/.well-known/openid-configuration`)
- `RegisteredClientRepository`, `OAuth2AuthorizationService`, `JWKSource` 인터페이스를 통해 각 컨텍스트 모듈과 어댑터 패턴으로 연동 가능
- Spring Boot 4.x 공식 지원

**제약:**
- `sso-client-context`는 `RegisteredClientRepository` 어댑터를 구현하여 Spring Authorization Server에 제공
- `sso-key-context`는 `JWKSource` 어댑터를 구현하여 Vault Transit 키를 노출
- 다중 `SecurityFilterChain`은 명시적 `@Order`로 우선순위를 지정 (Authorization Server가 가장 높음)
- `sso-authorization-context`는 Order=1(Authorization Server), Order=3(Resource Server)만 정의. Order=2(Admin, `/admin/**`)의 `SecurityFilterChain` Bean은 `sso-admin-context`에서 정의한다 (상세: `docs/AGENT_SPEC.md` 2.5, 2.10)

---

### ADR-003: Argon2id 패스워드 해싱

**결정:** 패스워드 해싱 알고리즘으로 Argon2id를 사용하고, `DelegatingPasswordEncoder`로 감싼다.

**이유:**
- OWASP 최우선 권장 알고리즘 (메모리 하드, 사이드채널 저항)
- BCrypt 대비 GPU 병렬 공격에 훨씬 강함
- `DelegatingPasswordEncoder` 적용으로 향후 알고리즘 교체 시 기존 해시 호환 유지 가능

**파라미터 (OWASP 기준):**
- saltLength: 16 bytes
- hashLength: 32 bytes
- parallelism: 1
- memory: 65536 KB (64 MB)
- iterations: 3
- 저장 형태: `{argon2}$argon2id$v=19$...`

---

### ADR-004: 테넌트 식별 전략

**결정:** 서브도메인을 Primary 식별 수단으로 한다. `X-Tenant-ID` 헤더 Fallback은 **개발·로컬 프로파일에서만** 허용하고, **운영(production)에서는 금지**한다. M2M(`client_credentials`) 요청은 JWT의 `tenant_id` 클레임으로만 테넌트를 식별한다.

**이유:**
- 서브도메인은 브라우저 Cookie를 자동 격리하여 테넌트 간 세션 오염 방지
- OAuth2 `redirect_uri`에 테넌트 컨텍스트가 자연스럽게 포함됨 (`https://kakaobank.sso.company.com/...`)
- Okta, Auth0, Keycloak 등 업계 표준이 서브도메인 방식 채택
- 운영에서 임의 `X-Tenant-ID` 스푸핑을 막기 위해 헤더 Fallback을 제한한다

**구현 규칙:** (`docs/AGENT_SPEC.md` 섹션 3.4가 상세 근거)
- `TenantResolutionFilter`가 요청마다 테넌트 추출 → `TenantContextHolder` 저장 → MDC `tenantId` 주입
- 서브도메인 추출: `request.getServerName()` 기준 첫 세그먼트 (로컬·IP 호스트는 서브도메인 없음)
- **local/dev:** 서브도메인 실패 시 `X-Tenant-ID` 헤더 Fallback 허용
- **production:** 서브도메인 실패 시 `400 Bad Request` (헤더 Fallback 금지)
- **M2M:** `client_credentials` 토큰의 `tenant_id` 클레임만 사용, `X-Tenant-ID` 무시
- 인증 후 JWT의 `tenant_id`와 `TenantContextHolder` 일치 검증: `TenantJwtValidationFilter` (Order=2, 3 체인, JWT 이후). 불일치 시 `403` + `SECURITY.TENANT_MISMATCH`
- 테넌트 미식별 시 `400 Bad Request` 반환

---

### ADR-005: 이벤트 스트림 전략 (Phase별 진화)

**Phase 1 — PostgreSQL Outbox + Redis Streams (현재 인프라 활용)**

- 도메인 Command와 같은 트랜잭션에서 `outbox` 테이블에 이벤트 INSERT (원자성 보장)
- `OutboxPoller`가 미발행 이벤트를 폴링하여 Redis Streams에 발행
- 감사 이벤트 Consumer: Redis Streams → PostgreSQL `audit` 테이블 저장
- 보안 이벤트 Consumer: Redis Streams → 이상 탐지 룰 엔진
- 운영 환경 Redis: AUTH·TLS·ACL (`AGENT_SPEC.md` 10.6)

**Phase 2 — Kafka 도입 (트래픽 증가 시)**

- Outbox Publisher를 Kafka Producer로 교체 (소비자 코드는 변경 없음)
- Topic 구성: `sso.events.authentication`, `sso.events.security`, `sso.events.audit.dlq`
- 장기 보관 이벤트: Kafka → S3 Parquet (Athena 조회)

---

### ADR-006: 감사 로그 불변성 보장

**결정:** PostgreSQL의 Rule 메커니즘과 **HMAC-SHA256** 기반 checksum으로 감사 로그 무결성·불변성을 보장한다. HMAC 키는 Vault KV(`secret/sso/audit-hmac-key`)에서 주입한다.

**구현:**
- `CREATE RULE no_delete_audit AS ON DELETE TO audit.audit_events DO INSTEAD NOTHING`
- `CREATE RULE no_update_audit AS ON UPDATE TO audit.audit_events DO INSTEAD NOTHING`
- DB 사용자에게 `INSERT`, `SELECT`만 부여 (`DELETE`, `UPDATE` 권한 없음)
- 각 이벤트 저장 시 주요 필드에 대한 **HMAC-SHA256** checksum을 함께 저장 (단순 SHA-256 비사용 — `AGENT_SPEC.md` 4.4)
- 키 로테이션: 이전 키로 검증 후 신규 키로 재서명하는 배치 절차
- `@TransactionalEventListener(phase = AFTER_COMMIT)`로 도메인 트랜잭션 성공 후에만 기록
- 저장 실패 시 `sso.audit.persistence.failures` 메트릭, 연속 실패 시 `SECURITY.AUDIT_PIPELINE_DEGRADED` 별도 경로 기록 (`AGENT_SPEC.md` 2.8)

---

## 3. 모듈 책임 정의

```
sso-platform/
├── sso-bootstrap            실행 진입점. 모든 컨텍스트 모듈을 조합하여 Spring Boot 애플리케이션으로 실행
├── sso-shared-kernel        공통 기반. AggregateRoot, DomainEvent, ValueObject, AuditEvent, 테넌트 필터·`TenantJwtValidationFilter`, 공통 예외
├── sso-tenant-context       테넌트 라이프사이클 관리. 멀티테넌트 격리의 루트 컨텍스트
├── sso-identity-context     사용자 계정, 자격증명(Argon2id), MFA 등록, UserDetailsService 구현
├── sso-client-context       OAuth2 클라이언트 앱 등록·관리. RegisteredClientRepository 어댑터
├── sso-authorization-context Spring Authorization Server 설정. SecurityFilterChain, TokenCustomizer
├── sso-federation-context   외부 IdP 연동 (SAML 2.0, OIDC). 소셜 로그인, Federated Identity 매핑
├── sso-session-context      SSO 세션 생명주기. Redis 기반. Single Logout(SLO) 트리거
├── sso-key-context          JWT 서명 키 생성·로테이션·폐기. Vault Transit 연동. JWKSource 어댑터
├── sso-audit-context        감사 이벤트 수신·저장·조회. 불변 PostgreSQL + Redis Streams Consumer
└── sso-admin-context        관리자 API. 테넌트·사용자·클라이언트 통합 관리. Admin 전용 FilterChain
```

### 모듈 의존 방향 (단방향 강제)

```
sso-bootstrap
  └── 모든 컨텍스트 모듈 (조합만, 로직 없음)

각 컨텍스트 모듈
  └── sso-shared-kernel (공통 기반만 의존)

컨텍스트 간 직접 의존 금지
  → 필요 시 shared-kernel의 인터페이스·이벤트로 간접 연결
```

---

## 4. Phase별 개발 로드맵

### Phase 0 — 공통 기반 구축 (Sprint 1~2)

**목표:** 모든 모듈이 공유하는 기반과 DB 마이그레이션 파이프라인 확립

| 작업 | 모듈 | 산출물 |
|---|---|---|
| AggregateRoot, DomainEvent, ValueObject, EntityId 구현 | sso-shared-kernel | 도메인 기반 클래스 |
| AuditEvent 공통 모델 정의 | sso-shared-kernel | AuditEvent record |
| SsoDomainException, ErrorCode 정의 | sso-shared-kernel | 공통 예외 계층 |
| TimeProvider, RandomIdGenerator 구현 | sso-shared-kernel | 테스트 가능한 유틸리티 |
| Flyway 의존성 추가 및 마이그레이션 구조 설정 | sso-bootstrap | db/migration/ 디렉터리 구조 |
| Logback JSON 설정 (docker 프로파일) | sso-bootstrap | logback-spring.xml |
| TenantContextHolder + TenantResolutionFilter (프로파일별 `X-Tenant-ID` 정책) | sso-shared-kernel | 테넌트 컨텍스트 전파 |
| TenantJwtValidationFilter (JWT `tenant_id` ↔ Holder 일치) | sso-shared-kernel | 리소스·Admin API 테넌트 격리 |
| MDC 자동 주입 필터 (tenantId, traceId) | sso-bootstrap | MdcPropagationFilter |
| docker-compose에 모니터링 스택 추가 | 인프라 | Prometheus, Grafana, Loki, Tempo |

**완료 기준:** 애플리케이션 기동 시 Flyway 마이그레이션 실행, JSON 로그 출력, 로컬 프로파일에서 테넌트 헤더·서브도메인 파싱 및 `TenantJwtValidationFilter` 배선 확인

---

### Phase 1 — 핵심 도메인 구축 (Sprint 3~4)

**목표:** 키 관리, 테넌트, 사용자 계정 도메인 완성

| 작업 | 모듈 | 산출물 |
|---|---|---|
| SigningKey Aggregate + Vault Transit 연동 | sso-key-context | VaultKeyStore, JwkSetProvider |
| `/.well-known/jwks.json` 엔드포인트 | sso-key-context | 공개 JWK 노출 |
| 키 로테이션 정책 + 스케줄러 | sso-key-context | KeyRotationScheduler |
| Tenant Aggregate + CRUD | sso-tenant-context | Tenant, TenantSettings |
| 테넌트 활성화/정지 로직 | sso-tenant-context | TenantStatusChangedEvent |
| User Aggregate + Credential Entity | sso-identity-context | User, Credential |
| Argon2id DelegatingPasswordEncoder 설정 | sso-identity-context | PasswordConfig |
| MfaEnrollment (TOTP 시크릿 Vault Transit 암호화, 백업코드 Argon2id 해시) | sso-identity-context | MfaEnrollment, Vault MFA 키 경로 |
| UserDetailsService 구현 | sso-identity-context | SsoUserDetailsService |
| Flyway: tenant, identity 스키마 DDL | sso-bootstrap | V1.x, V2.x 마이그레이션 |

**완료 기준:** 사용자 등록 → TOTP MFA 등록 → 로그인 API 동작 확인 (통합 테스트)

---

### Phase 2 — OAuth2 Authorization Server (Sprint 5~6)

**목표:** OAuth2 / OIDC 표준 프로토콜 완전 구현

| 작업 | 모듈 | 산출물 |
|---|---|---|
| OAuthClient Aggregate + Vault Secret 암호화 | sso-client-context | OAuthClient, ClientSecretEncryptor |
| RegisteredClientRepository 어댑터 | sso-client-context | SpringRegisteredClientAdapter |
| 클라이언트 시크릿 로테이션 UseCase | sso-client-context | RotateClientSecretUseCase |
| Flyway: client 스키마 DDL | sso-bootstrap | V3.x 마이그레이션 |
| Spring Authorization Server 설정 | sso-authorization-context | AuthorizationServerConfig |
| SecurityFilterChain Order=1·3 설정 | sso-authorization-context | Authorization Server + Resource Server만 (`AGENT_SPEC.md` 2.5) |
| OAuth2TokenCustomizer (tenantId, roles 클레임) | sso-authorization-context | TenantAwareTokenCustomizer |
| Authorization Code Flow + PKCE 검증 | sso-authorization-context | PKCE 필수 설정 |
| Client Credentials Flow | sso-authorization-context | M2M 토큰 발급 |
| Token Introspection / Revocation | sso-authorization-context | RFC 7662, RFC 7009 |
| Flyway: Spring Authorization Server 공식 DDL | sso-bootstrap | V4.x 마이그레이션 |

`/admin/**`용 Order=2 `SecurityFilterChain`은 `sso-admin-context`에서만 정의하며, 본 Phase에서는 미구현 가능. Phase 5에서 `AdminSecurityConfig`로 완성한다.

**완료 기준:** Authorization Code + PKCE Flow E2E 동작, ID Token 클레임 검증 (JUnit + Testcontainers)

---

### Phase 3 — 세션 관리 + 감사 파이프라인 (Sprint 7~8)

**목표:** SSO 세션 라이프사이클과 감사 이벤트 스트림 구축

| 작업 | 모듈 | 산출물 |
|---|---|---|
| SsoSession Aggregate + Redis 저장소 | sso-session-context | SsoSession, SsoSessionRepository |
| Single Logout (SLO) 트리거 + 백채널 SSRF 방지·HTTPS·등록 `logoutUri`만·토큰 서명 | sso-session-context | PropagateLogoutUseCase (`AGENT_SPEC.md` 2.7) |
| 동시 세션 수 제한 정책 | sso-session-context | ConcurrentSessionPolicy |
| 디바이스 정보 + GeoIP 수집 | sso-session-context | DeviceInfo, GeoIpResolver |
| Flyway: session 메타데이터 테이블 | sso-bootstrap | V5.x 마이그레이션 |
| Outbox 테이블 DDL + OutboxPoller | sso-shared-kernel | OutboxEvent, OutboxPoller |
| Redis Streams Producer / Consumer | sso-audit-context | StreamEventPublisher |
| AuditEvent 수신 → PostgreSQL 저장 (HMAC checksum, 실패 메트릭·파이프라인 저하 알림) | sso-audit-context | AuditEventPersistenceHandler |
| 이상 탐지 룰 엔진 (Brute Force, Impossible Travel) | sso-audit-context | SecurityAnomalyDetector |
| 감사 로그 조회 API (역할별 테넌트 격리, 스트리밍 분당 5회 제한) | sso-audit-context | AuditQueryController |
| Flyway: audit 스키마 + 파티셔닝 DDL | sso-bootstrap | V7.x 마이그레이션 |

**완료 기준:** 로그인 이벤트 발생 시 감사 테이블에 불변 기록 확인, SLO 동작 확인

---

### Phase 4 — 외부 IdP 연동 (Sprint 9~10)

**목표:** SAML 2.0 및 OIDC Federation 지원

| 작업 | 모듈 | 산출물 |
|---|---|---|
| IdentityProvider Aggregate | sso-federation-context | IdentityProvider |
| SAML 2.0 SP-initiated SSO | sso-federation-context | SamlAuthenticationFilter |
| SAML SLO (Single Logout) | sso-federation-context | SamlLogoutHandler |
| OIDC Provider 연동 (Google, Microsoft Entra ID) | sso-federation-context | OidcFederationConfig |
| FederatedIdentity 매핑 (외부 ID → 내부 User) | sso-federation-context | FederatedIdentityMapper |
| 속성 매핑 규칙 설정 (클레임 변환) | sso-federation-context | ClaimTransformationRule |
| 페더레이션 보안 (OIDC state/nonce, SAML 서명·InResponseTo, IdP 인증서 핀닝, 계정 링크 재인증) | sso-federation-context | `AGENT_SPEC.md` 2.9 준수 |
| Flyway: federation 스키마 DDL | sso-bootstrap | V8.x 마이그레이션 |

**완료 기준:** Google OIDC 로그인 → 내부 User 생성/연결 → ID Token 발급 E2E 확인

---

### Phase 5 — 관리자 포털 + 운영 강화 (Sprint 11+)

**목표:** 관리 API, 모니터링 완성, 보안 강화

| 작업 | 모듈 | 산출물 |
|---|---|---|
| Admin 전용 SecurityFilterChain (Order=2, `/admin/**`, Admin 전용 JWT) | sso-admin-context | AdminSecurityConfig |
| Admin Role 계층 (`ROLE_SUPER_ADMIN`, `ROLE_TENANT_ADMIN`; `ROLE_ADMIN` 명칭 미사용) | sso-admin-context | AdminRole |
| 테넌트·사용자·클라이언트 통합 관리 API | sso-admin-context | AdminController |
| 강제 로그아웃 / 계정 잠금 API | sso-admin-context | ForceLogoutUseCase |
| Rate Limiting (Redis 기반 Sliding Window) | sso-bootstrap | RateLimitingFilter |
| 보안 헤더 설정 (HSTS, CSP, X-Frame-Options) | sso-authorization-context | SecurityHeadersConfig |
| Micrometer 커스텀 메트릭 등록 | 각 컨텍스트 모듈 | SSO 비즈니스 메트릭 |
| Prometheus AlertManager 룰 작성 | 인프라 | alert-rules.yml |
| Grafana 대시보드 프로비저닝 | 인프라 | dashboard JSON 4종 |
| OpenTelemetry 분산 추적 설정 | sso-bootstrap | OtelConfig |
| Spring Cloud Vault 연동 (AppRole) | sso-bootstrap | VaultConfig |
| CORS·CSRF·보안 헤더·Actuator CIDR·Redis TLS/ACL | 공통 | `AGENT_SPEC.md` 섹션 10 |

**완료 기준:** 전체 모니터링 스택 가동, 브루트포스 알림 동작 확인

---

## 5. 인프라 구성

### 현재 (docker-compose.yml)

| 서비스 | 이미지 | 포트 | 용도 |
|---|---|---|---|
| postgres | postgres:16-alpine | 5432 | 도메인 데이터, 감사 로그 |
| redis | redis:7-alpine | 6379 | 세션, 이벤트 스트림, 캐시 |
| vault | hashicorp/vault:1.16 | 8200 | 시크릿, JWT 키 관리 |
| sso-bootstrap | 로컬 빌드 | 8080 | 애플리케이션 |

### 추가 예정 (Phase 0 ~ Phase 5)

| 서비스 | 이미지 | 포트 | 용도 |
|---|---|---|---|
| prometheus | prom/prometheus:v2.51.0 | 9090 | 메트릭 수집 |
| grafana | grafana/grafana:10.4.0 | 3000 | 시각화, 알림 |
| loki | grafana/loki:3.0.0 | 3100 | 로그 수집 |
| tempo | grafana/tempo:2.4.0 | 4318, 3200 | 분산 추적 |
| postgres-exporter | prometheuscommunity/postgres-exporter:v0.15.0 | 9187 | PostgreSQL 메트릭 |
| redis-exporter | oliver006/redis_exporter:v1.58.0 | 9121 | Redis 메트릭 |

### Actuator 포트 분리

- 애플리케이션 포트: `8080` (외부 노출)
- Actuator/메트릭 포트: `8081` (내부망 CIDR·허용 IP만, Prometheus 스크래핑 전용 — `AGENT_SPEC.md` 10.7)

---

## 6. 보안 정책

### 다중 SecurityFilterChain 경로 정책

| 경로 | FilterChain | 소유 모듈 | 인증 방식 |
|---|---|---|---|
| `/oauth2/**`, `/.well-known/**`, `/userinfo` | Authorization Server (Order=1) | sso-authorization-context | OAuth2 / OIDC |
| `/admin/**` | Admin (Order=2) | sso-admin-context | JWT Bearer, `ROLE_SUPER_ADMIN` 또는 `ROLE_TENANT_ADMIN` (Admin 전용 발급 토큰) |
| `/api/**` | Resource Server (Order=3) | sso-authorization-context | JWT Bearer (JWK 검증) |
| `/actuator/**` (health 제외) | — | sso-bootstrap 등 | 내부망 CIDR + 선택적 Basic Auth |
| `/actuator/health` | — | — | 공개 |

상세 CORS·CSRF·보안 헤더·Rate Limit·Redis·SQLi 방지: `docs/AGENT_SPEC.md` 섹션 10.

### Rate Limiting 기본값

| 대상 | 제한 | 윈도우 |
|---|---|---|
| 로그인 시도 (계정당) | 5회 실패 | 10분 슬라이딩 |
| 로그인 시도 (IP당) | 20회 실패 | 10분 슬라이딩 |
| Token 요청 (클라이언트당) | 100 req/s | 1초 |
| `/oauth2/authorize` | 30 req/min | 1분 |
| 감사 로그 스트리밍 다운로드 (관리자 1인당) | 5 req/min | 1분 |

### 토큰 TTL 기본값

| 토큰 종류 | TTL | 조정 가능 여부 |
|---|---|---|
| Access Token | 1시간 | 클라이언트별 설정 |
| Refresh Token | 14일 | 클라이언트별 설정 |
| ID Token | 1시간 | 고정 |
| Authorization Code | 5분 | 고정 |

### MFA 정책

- TOTP (RFC 6238) 기반, 30초 유효 코드
- TOTP 시크릿: Vault Transit 암호화 후 DB 저장 (`transit/keys/sso-mfa-secret`)
- 백업 코드 10개: 개별 Argon2id 해시 저장, 1회 사용 후 소진·삭제
- 테넌트 관리자가 MFA 필수 여부 설정 가능
- 신규 기기 감지 시 강제 MFA 챌린지

---

## 7. 규정 준수 체크리스트

| 항목 | 기준 | 구현 위치 |
|---|---|---|
| 패스워드 강도 정책 | 테넌트별 설정 | TenantSettings |
| 감사 로그 최소 보관 | 5년 (금융보안원) | Flyway + S3 아카이빙 |
| 개인정보 열람 요청 | GDPR Article 15 | `/admin/audit/timeline/{userId}` |
| 접근 로그 무결성 | 위변조 불가 | HMAC-SHA256 Checksum(Vault KV 키) + DB Rule (`AGENT_SPEC.md` 4.4) |
| 암호화 전송 | TLS 1.2+ 강제 | HSTS 헤더 |
| 최소 권한 원칙 | DB 사용자 권한 분리 | INSERT/SELECT Only |
