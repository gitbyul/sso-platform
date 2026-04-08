# SSO Platform — 기술 요구사항 문서 (TRD)

| 항목 | 내용 |
|------|------|
| 상위 문서 | [`PRD.md`](./PRD.md) |
| 구현 강제 규칙 | [`AGENTS.md`](../../AGENTS.md), `.cursor/rules/*.mdc` |
| 로드맵·ADR | [`DEVELOPMENT_PLAN.md`](./DEVELOPMENT_PLAN.md) |
| DDL·인덱스 | [`DATABASE_RULES.md`](./DATABASE_RULES.md) |
| 기준일 | 2026-04-08 |

**적용 원칙**

1. **TRD**는 PRD의 FR/NFR을 **검증 가능한 기술 산출물·경계·인터페이스**로 풀어낸 문서이다.
2. **충돌 시 우선순위:** `AGENTS.md` + `.cursor/rules/*.mdc` > 본 TRD > `DEVELOPMENT_PLAN.md` 서술 > `PRD.md` 서술.
3. 본 문서의 요구사항 ID **TR-xxx**는 이슈·PR·테스트 설계에서 PRD의 **FR-/NFR-** 와 연결해 추적한다.

---

## 1. PRD → TRD 추적 매트릭스

### 1.1 기능 요구 (요약)

| PRD ID | TRD 절(기술 정의) | 검증(최소) |
|--------|-------------------|------------|
| FR-T-01 ~ FR-T-04 | §2.1, §4.1, §5.1 | 단위·통합: 서브도메인 해석, M2M `tenant_id`, JWT 불일치 403 + 감사 이벤트 |
| FR-I-01 ~ FR-I-04 | §3.1, §4.2, §5.2 | Argon2id 인코딩, MFA Vault 경로, 백업코드 1회성 |
| FR-O-01 ~ FR-O-05 | §4.3, §5.3 | Discovery/JWKS, PKCE 필수, Token Customizer 클레임, introspection/revocation |
| FR-C-01 ~ FR-C-03 | §4.4 | `RegisteredClientRepository` 어댑터, Admin 전용 등록 경로 |
| FR-S-01 ~ FR-S-03 | §4.5, §5.4 | Redis 세션, SLO URL 검증·SSRF 방지 |
| FR-F-01 ~ FR-F-04 | §4.6 | SAML/OIDC 연동, `agent-module-federation-context.mdc` 보안 항목 |
| FR-A-01 ~ FR-A-05 | §4.7, §5.5 | FilterChain Order, Rate limit 429+Retry-After, CORS/헤더 |
| FR-L-01 ~ FR-L-04 | §4.8 | Outbox→Streams→audit, HMAC, 불변 DB 규칙 |
| FR-E-01 ~ FR-E-02 | §4.9 | 트랜잭션 Outbox, Redis Streams, Phase 2 Kafka 전환 전제 |
| FR-SCIM-* | §6.1 | 미구현 Epic: REST API 계약·인증·감사 별도 스펙 |
| FR-UX-* | §6.2 | 테넌트 메타·정적 리소스 또는 템플릿 |
| FR-CA-* | §6.3 | 디바이스/위험 스코어·step-up 훅 |
| FR-WA-* | §6.4 | WebAuthn 서버·저장소 암호화 |

### 1.2 비기능 요구

| PRD ID | TRD 절 | 기술적 의미 |
|--------|--------|-------------|
| NFR-P-01, NFR-P-02 | §7.1 | 부하 테스트 시나리오, Redis 클러스터/스티키 정책 문서화 |
| NFR-A-01, NFR-A-02 | §7.2 | SLO 모니터링, 업타임 대시보드, 유지보수 절차 |
| NFR-D-01 ~ NFR-D-04 | §7.3 | 백업·복구 Runbook, 감사 아카이빙 파이프라인 |
| NFR-S-01, NFR-S-02 | §5.5, §7.4 | TLS, Vault AppRole, Actuator 네트워크 분리 |
| NFR-O-01 | §7.5 | MDC, Micrometer 태그, OTEL 배포 |
| NFR-Q-01 | §8 | TDD, Testcontainers, `tdd-strict.mdc` 보안 회귀 |

---

## 2. 시스템 컨텍스트·배포

### TR-ARCH-01 (대응 ADR-001, PRD 코어)

- **실행 단일 모듈:** `sso-bootstrap`만 `@SpringBootApplication`. 나머지 컨텍스트는 `java-library`.
- **의존 방향:** 각 바운디드 컨텍스트는 `sso-shared-kernel`:만 직접 의존; 컨텍스트 간 직접 의존 금지.
- **패턴:** CQRS(`command` / `query`), 도메인 이벤트 + Outbox (FR-E-01).

### TR-ARCH-02 (대응 PRD Self-Hosted)

- **기본 배포:** 단일 프로세스(또는 수평 복제 + Redis 세션/캐시 공유). 스케일아웃 시 **JWT stateless + Redis 세션** 정합성 유지.
- **외부 의존:** PostgreSQL 16, Redis 7, Vault 1.16 (`agent-project-context.mdc`와 동일).

### TR-ARCH-03 (관측)

- 애플리케이션 HTTP: 설계상 **8080**, Actuator/스크랩 메트릭: **8081** (`DEVELOPMENT_PLAN.md` 5절). 운영에서는 CIDR·방화벽으로 8081 제한.

---

## 3. 보안 아키텍처 (강제)

### TR-SEC-01 — SecurityFilterChain 순서

| Order | 경로 | 소유 모듈 | 규칙 파일 |
|-------|------|-----------|-----------|
| 1 | `/oauth2/**`, `/.well-known/**`, `/userinfo` | `sso-authorization-context` | `agent-authorization-server.mdc` |
| 2 | `/admin/**` | `sso-admin-context` | `agent-module-admin-context.mdc` |
| 3 | `/api/**` | `sso-authorization-context` | 동일 |

- Order=1: CSRF 유지. Order=2·3: JWT Bearer 기반일 때 CSRF disable 명시 (`agent-security-platform.mdc`).

### TR-SEC-02 — 테넌트 결정·검증

- **TenantResolutionFilter:** 서브도메인 → `TenantContextHolder`, MDC `tenantId`.
- **프로파일:** `local`/`dev`만 `X-Tenant-ID` fallback; **`production` 금지** (ADR-004).
- **TenantJwtValidationFilter:** JWT 파싱 이후 `tenant_id` ↔ Holder 일치; 불일치 **403** + `SECURITY.TENANT_MISMATCH` (tdd-strict 회귀).
- **client_credentials:** `X-Tenant-ID` 무시, 토큰 `tenant_id`만 사용.

### TR-SEC-03 — OAuth/OIDC

- 모든 `authorization_code` 클라이언트: `requireProofKey(true)` (PKCE 필수).
- Access/ID Token: `tenant_id`, `roles`, `mfa_verified`, `amr` 등 PRD와 정합 (`OAuth2TokenCustomizer`).
- Issuer: 테넌트별 동적 URI 패턴 유지 (`agent-authorization-server.mdc`).
- `RegisteredClientRepository.save` — 런타임 미사용; **Admin API로만** 클라이언트 반영 (FR-C-03).

### TR-SEC-04 — 비밀·암호화

- 패스워드: Argon2id, `DelegatingPasswordEncoder`, OWASP 파라미터 (`DEVELOPMENT_PLAN.md` ADR-003).
- MFA TOTP 시크릿: Vault Transit 암호화 후 저장.
- 클라이언트 시크릿: 저장 시 암호화; M2M 시크릿 길이 **≥ 32바이트** (`agent-security-platform.mdc`).

### TR-SEC-05 — 키(JWT)

- `sso-key-context`: Vault Transit 연동, `JWKSource` 어댑터, `/.well-known/jwks.json`.
- 키 로테이션: 스케줄 + JWKS 캐시 구간 검증 (tdd-strict 회귀).

### TR-SEC-06 — Rate limit·헤더

- Redis Sliding Window, 초과 시 **429** + **Retry-After**, `SECURITY.RATE_LIMIT_EXCEEDED`.
- HSTS, CSP, `Cache-Control: no-store`(인증 응답), CORS 경로별 정책 (`agent-security-platform.mdc`).

---

## 4. 도메인별 기술 산출물

### 4.1 테넌트 (`sso-tenant-context`)

- Aggregate: Tenant, TenantSettings (패스워드/MFA/세션 정책).
- API: Admin 경로 하에 CRUD·상태 전환 (FR-T-01).
- DB: `tenant` 스키마, Flyway 버전 규칙 `agent-flyway-versioning.mdc`.

### 4.2 신원 (`sso-identity-context`)

- User, Credential, MfaEnrollment, `UserDetailsService` 구현체.
- 백업 코드: **건당 Argon2id**, 사용 후 삭제.

### 4.3 인가 서버 (`sso-authorization-context`)

- Spring Authorization Server 설정, Resource Server `/api/**`.
- RFC 7662 / RFC 7009 연동 (FR-O-04).

### 4.4 클라이언트 (`sso-client-context`)

- OAuthClient Aggregate, `SpringRegisteredClientAdapter`, 시크릿 로테이션 유스케이스.

### 4.5 세션 (`sso-session-context`)

- Spring Session + Redis, 동시 세션 정책.
- SLO: 허용 `logoutUri`만, 사설 IP 차단 등 (tdd-strict SLO 시나리오).

### 4.6 페더레이션 (`sso-federation-context`)

- SAML SP-initiated, OIDC IdP, `FederatedIdentityMapper`, 클레임 규칙.
- State/nonce, 서명 검증, 계정 링크 재인증 (`agent-module-federation-context.mdc`).

### 4.7 감사 (`sso-audit-context`)

- Consumer: Redis Streams → PostgreSQL `audit` 스키마.
- HMAC-SHA256 체크섬, Vault KV 키; UPDATE/DELETE 차단 Rule + DB 권한 최소화.
- 스트리밍 다운로드 Rate: PRD/개발계획 수치 준수.

### 4.8 이벤트 (`sso-shared-kernel` + 컨텍스트)

- 같은 트랜잭션에서 도메인 커밋 + `outbox` INSERT.
- Poller → Redis Streams; 감사·보안 소비자 분리.

---

## 5. 인터페이스·데이터 계약

### 5.1 HTTP 공개 표면 (필수)

- OIDC Discovery, JWKS, OAuth2 승인·토큰 엔드포인트, UserInfo, Introspection, Revocation.
- 관리자: `/admin/**` — Admin 전용 JWT, `ROLE_SUPER_ADMIN` / `ROLE_TENANT_ADMIN` (`ROLE_ADMIN` 명칭 미사용).

### 5.2 토큰 클레임 (최소)

- `tenant_id` (필수), `roles`, `mfa_verified`, `amr` (시나리오별).

### 5.3 TTL 기본값 (`DEVELOPMENT_PLAN.md` 6절과 동기)

- Access 1h, Refresh 14d, ID Token 1h, Auth Code 5m — 클라이언트·테넌트 설정으로 덮어쓰기 가능 범위는 구현에서 명시.

### 5.4 감사 이벤트

- 스키마·불변성·체크섬 필드: `agent-audit-spec.mdc`, `agent-module-audit-context.mdc`.
- 실패 메트릭: `sso.audit.persistence.failures` 등 계획서 명칭 준수.

### 5.5 관측 필드

- 구조화 로그·MDC: `tenantId`, `traceId` (PRD NFR-O-01).

---

## 6. 미구현 Epic — 기술 전제 (PRD §7.5)

구현 전 별도 기술 스펙(SAD 보조 문서)으로 세분화한다.

### 6.1 SCIM 2.0 (TR-SCIM-*)

- **경로 네임스페이스:** 예) `/scim/v2/**` — 별도 Resource Server 체인 또는 Order 명시.
- **인증:** 통합 토큰 + 테넌트 바인딩, 또는 mTLS; SCIM 요청은 **서버-투-서버**만.
- **멱등:** ETag/If-Match 또는 요청 idempotency-key 표준 정하기.
- **감사:** 각 mutating 요청을 `audit` 파이프라인에 기록.

### 6.2 브랜딩 (TR-UX-*)

- 테넌트별 메타: 로고 URL, hex 색상, 문구 키; 정적 리소스는 CDN 또는 앱 내 템플릿.
- 커스텀 도메인: TLS SNI·테넌트 매핑 테이블, 인증서 자동갱신(ACME) 여부 결정.

### 6.3 조건부 접근 (TR-CA-*)

- 로그인 시 디바이스 지문·IP/Geo 저장소(예: `identity` 또는 `session` 스키마).
- Step-up: AMR 재요구 또는 별도 `step_up_required` 플래그와 Authorization Server 연동.

### 6.4 WebAuthn (TR-WA-*)

- FIDO2 서버 라이브러리 선택, 크레덴셜 공개키 저장, 테넌트·사용자 FK.
- 복구: 기존 MFA 정책과 상호 배타 규칙 정의.

---

## 7. 운영·성능·DR (기술)

### 7.1 성능 (NFR-P-*)

- 부하 테스트: 로그인, authorize, token, JWKS, introspection 시나리오별 **P95/P99** 기록.
- Redis: 연결 풀, 타임아웃, 장애 시 서킷 브레이커 정책.

### 7.2 가용성 (NFR-A-*)

- **99.9%/년** 대응: 다중 인스턴스 + 헬스체크 + 롤링 배포; DB/Redis 단일 장애 대비 설계 문서화.

### 7.3 DR (NFR-D-*)

- PostgreSQL: 연속 아카이브·PITR 목표 시점.
- Redis: AOF + 복제; 세션 유실 시 사용자 영향(재로그인) 명시.
- Vault: 백업·seal 복구 훈련.
- 감사: 월별 파티션 + Object Storage 아카이빙 + 조회 경로(Athena 등)는 운영 Runbook.

### 7.4 비밀·환경

- 운영 Vault: AppRole 등; **dev `-dev` 루트 토큰 금지** (`README.md`).
- 환경 변수·`@ConfigurationProperties`로 외부화; 기본값으로 멀티테넌시·보안을 깨지 않음.

### 7.5 관측성 구현

- Micrometer: 커스텀 메트릭 (`MeterRegistry`), Prometheus 스크랩.
- OTEL: exporter·샘플링 비율·Tempo 연동.
- 로그: JSON(Logback), Loki 라벨에 `tenantId` 포함 여부는 개인정보 정책과 정합.

---

## 8. 품질·테스트 계약

### TR-QA-01

- 신규 프로덕션 코드는 **실패 테스트(Red) 선행** 없이 추가하지 않는다 (`tdd-strict.mdc`).

### TR-QA-02

- DB 통합: **PostgreSQL** Testcontainers (H2 대체 금지).
- Redis 의존: Redis Testcontainers.

### TR-QA-03 — 보안 회귀 (필수 시나리오, `tdd-strict.mdc`)

- JWT `tenant_id` ≠ 요청 테넌트 → 403 + 감사.
- PKCE 누락 authorization code 요청 거부.
- 일반 사용자 토큰 `/admin/**` 거부.
- JWKS 캐시 구간 키 로테이션.
- SLO `logoutUri` 사설/비허용 차단.
- Rate limit → 429 + Retry-After.
- 감사 로그 HMAC 검증.
- MFA 백업코드 재사용 거부.
- OIDC nonce 리플레이 거부.

### TR-QA-04

- Fake는 `src/test/**`만, `Fake` 접미사; 검증 없는 stub-only 테스트 금지.

---

## 9. 버전·마이그레이션

- Flyway: `agent-flyway-versioning.mdc` + `DATABASE_RULES.md` §2 명명.
- 브레이킹 API: Major 정책·릴리스 노트에 OAuth/OIDC 메타데이터 영향 명시.

---

## 10. 문서 로드맵

| 문서 | 역할 |
|------|------|
| PRD | 무엇을·왜 (제품) |
| TRD | 어떻게 검증·구현할지 (기술 계약) |
| DEVELOPMENT_PLAN | 일정·ADR·Phase |
| AGENTS + rules | 일상 구현 강제 규칙 |
| DATABASE_RULES | DDL 실무 |

---

## 11. 승인 (초안)

| 역할 | 이름 | 일자 |
|------|------|------|
| 테크 리드 | | |
| 보안 | | |
| 아키텍처 | | |
