# SSO Platform — 제품 요구사항 문서 (PRD)

| 항목 | 내용 |
|------|------|
| 저장소 | [github.com/gitbyul/sso-platform](https://github.com/gitbyul/sso-platform) |
| 기준일 | 2026-04-08 |
| 관련 문서 | [`AGENTS.md`](../../AGENTS.md), [`DEVELOPMENT_PLAN.md`](./DEVELOPMENT_PLAN.md), [`DATABASE_RULES.md`](./DATABASE_RULES.md), [`TRD.md`](./TRD.md), [`.cursor/rules/`](../rules/) |

**우선순위:** 구현·보안·코드 구조는 `AGENTS.md`와 `.cursor/rules/*.mdc`가 이 PRD보다 구체적인 강제 규칙으로 우선한다. 이 PRD는 제품·운영·로드맵 의사결정과 외부 이해관계자 정렬용이다. **기술적으로 “무엇을 어떻게” 충족하는지는 [`TRD.md`](./TRD.md)가 단일 기준**이다.

---

## 1. 배경·목표

### 1.1 문제 정의

B2B SaaS·엔터프라이즈 내부 플랫폼은 테넌트(조직)마다 인증 정책·외부 IdP·감사·규제 요구가 다르다. 외부 IdP(SaaS)만으로는 데이터 주권·정책 맞춤·비용·벤더 종속 이슈가 생길 수 있어, **자체 호스팅 가능한 표준 준수 SSO**가 필요하다.

### 1.2 제품 비전

**OAuth 2.0 / OIDC를 완전 준수하고, SAML·OIDC 페더레이션·멀티테넌트·감사·운영 관측까지 포함한 엔터프라이즈급 SSO 플랫폼**을 **단일 제품**으로 제공한다.

### 1.3 성공 지표 (KPI)

| KPI | 목표 방향 |
|-----|-----------|
| 가용성 | 연간 **99.9%**를 설계 목표로 한다. 배포 토폴로지·검증은 §6.2·§11과 Runbook에서 구체화한다. |
| 보안 회귀 | CI에서 필수 보안 시나리오 테스트 통과 (`.cursor/rules/tdd-strict.mdc`) |
| 표준 호환 | OAuth 2.0 / OIDC 클라이언트·리소스 서버가 **표준 메타데이터·엔드포인트**만으로 연동 가능 |
| 규제 대응 | 감사 로그 불변성·보존·개인정보 처리 요구를 추적 가능 (§7, §11) |
| 테넌트 격리 | JWT `tenant_id`와 요청 컨텍스트 불일치 시 **403** 및 보안 이벤트 (ADR-004) |

---

## 2. 제품 제공 형태 (서비스 모델)

다음 두 형태를 **제품 전략에서 명시**하고, 계약·격리·과금 단위를 분리한다.

| 모델 | 설명 | 전제 |
|------|------|------|
| **A. Self-Hosted 제품** | 고객 인프라에 배포. 라이선스·지원 계약으로 제공. | 데이터·키는 고객 경계 내. 운영 책임은 고객+벤더 지원 범위 협의. |
| **B. 관리형 멀티테넌트 SaaS (선택)** | 공급사가 호스팅. 테넌트별 논리·물리 격리 정책 명시. | 데이터 레지던시(리전)·SLA 등별·청구 단위(MAU/테넌트) 정의 필요. |

**본 저장소 코드베이스**는 기본적으로 **모델 A(Self-Hosted)** 배포를 전제로 한다. 모델 B를 제공할 경우 **배포·네트워킹·청구·온보딩 포털**은 별도 Epic으로 PRD 하위 문서(운영 PRD)에 둔다.

---

## 3. 이해관계자·페르소나

| 페르소나 | 핵심 니즈 |
|-----------|-----------|
| 플랫폼 운영자 (SRE/보안) | 키 로테이션, Vault, Redis/DB HA, 관측·알림, Rate limit, DR |
| 테넌트 관리자 | 사용자·MFA·OAuth 클라이언트·정책, IdP 연동 |
| 최고 관리자 | 크로스테넌트 운영, 감사·위험 대응 |
| 애플리케이션 개발자 | OIDC/OAuth, JWKS, introspection/revocation |
| 최종 사용자 | SSO, MFA, SLO |
| 컴플라이언스 | 감사 추적, 보존, 개인정보 주체 권리 |

---

## 4. 범위 요약

### 4.1 핵심 포함 (코어 제품)

- 멀티테넌시: 서브도메인 primary, 운영 `X-Tenant-ID` fallback 금지, M2M은 토큰 `tenant_id` ([`DEVELOPMENT_PLAN.md` ADR-004](./DEVELOPMENT_PLAN.md))
- 계정·자격증명: Argon2id, 테넌트별 패스워드 정책, TOTP MFA + 백업코드 (Vault Transit)
- Authorization Server: Authorization Code + **PKCE 필수**, Client Credentials, 토큰 클레임(`tenant_id`, `roles`, `mfa_verified`, `amr` 등)
- 토큰 운영: Introspection, Revocation, JWK, Vault 기반 키·로테이션
- 세션·SLO: Redis, 등록 `logoutUri`, SSRF 방지 등 세션 모듈 규칙
- 감사: Outbox → Redis Streams → 불변 저장, HMAC 무결성
- 페더레이션: SAML 2.0, OIDC IdP, 계정 링크·클레임 매핑·보안 통제
- 관리 API: `/admin/**` 전용 체인, `ROLE_SUPER_ADMIN` / `ROLE_TENANT_ADMIN`

### 4.2 확장·후순위 (별도 Epic)

| 영역 | 설명 |
|------|------|
| **SCIM 2.0** | 사용자·그룹 자동 프로비저닝(Inbound/Outbound). 엔터프라이즈 IdP·ITSM 연동에 필수인 경우가 많음. |
| **브랜딩·로그인 UX** | 테넌트별 로고, 문구, 테마, 호스트 커스텀 로그인 페이지 |
| **조건부 접근** | 디바이스·위치·위험 점수·앱 민감도에 따른 단계적 인증·세션 재검증 |
| **WebAuthn / 패스키** | 피싱 저항 패스워드리스. TOTP와 병행 정책 |
| **공식 인증·감사** | SOC 2, ISO 27001 등: 통제 목록·증적은 컴플라이언스 패키지로 관리 |

---

## 5. MVP vs 1.0 범위

| 구분 | MVP (최소 제품) | 1.0 (엔터프라이즈 기본 판) |
|------|-----------------|-----------------------------|
| 테넌트·사용자·패스워드·Argon2 | 포함 | 포함 |
| TOTP MFA + 백업코드 | 포함 | 포함 |
| OAuth2/OIDC AS, PKCE, M2M, JWKS | 포함 | 포함 |
| Token introspection / revocation | 포함 | 포함 |
| Redis 세션·기본 SLO | 포함 | 포함 |
| 감사 Outbox→Streams→DB, HMAC | 포함 | 포함 |
| 관리자 API·Rate limit·보안 헤더 | MVP는 최소 Admin | 전역 완성 (Phase 5 정합) |
| 페더레이션 (SAML/OIDC) | **선택 또는 MVP 이후** | **포함** (Phase 4 목표) |
| SCIM 2.0 | 제외 | **권장 (별도 마이너 1.x)** |
| 브랜딩 UI | 제외 또는 최소(공통 페이지) | 테넌트별 설정 |
| 조건부 접근·위험 엔진 | 제외 | 정책 MVP(위험 점수·단계적 MFA) |
| WebAuthn | 제외 | **1.x 후보** |
| 관측 풀스택(Prometheus/Loki/Tempo) | MVP는 핵심 메트릭·로그 | 로드맵과 동일 |
| DR 문서·RPO/RTO 수치 | 초안 | 승인된 Runbook |

---

## 6. 비기능 요구사항 (NFR)

### 6.1 성능·용량

| ID | 요구사항 |
|----|----------|
| NFR-P-01 | 인증·토큰 발급 **P95 지연 목표**를 정의하고 부하 테스트로 검증한다. (초기 제안: 사내 기준치 설정 후 PRD 개정) |
| NFR-P-02 | 수평 확장 시 **Stateless JWT + Redis 세션** 전제를 유지하고, 세션 스토어 HA 전략을 문서화한다. |

### 6.2 가용성·SLA

| ID | 요구사항 |
|----|----------|
| NFR-A-01 | **목표 가용성 99.9%/년**을 설계 기준으로 한다. 고객 계약별 SLA는 모델 A/B에 따라 상이. |
| NFR-A-02 | 계횃된 유지보수 윈도·공지 기준(Self-Hosted: 고객 정책 / SaaS: 공급사 정책)을 정의한다. |

### 6.3 재해복구 (DR)·백업

| ID | 요구사항 |
|----|----------|
| NFR-D-01 | **RPO(데이터)**·**RTO(서비스)** 목표를 Runbook에 명시한다. (예시 초안: RPO ≤ 1시간, RTO ≤ 4시간 — 조직 승인 후 확정) |
| NFR-D-02 | PostgreSQL 백업·PITR, Redis 내구성(AOF/복제), Vault 백업·언실(Mount) 복구 절차를 포함한다. |
| NFR-D-03 | 감사 로그 **장기 보존**(예: 5년) 및 아카이빙(S3 등)은 `DEVELOPMENT_PLAN.md` 7절과 정합되게 운영 절차로 관리한다. |
| NFR-D-04 | 정기 **복구 리허설**(분기 1회 권장)을 컴플라이언스 체크리스트에 포함한다. |

### 6.4 보안·관측·품질

| ID | 요구사항 |
|----|----------|
| NFR-S-01 | TLS 1.2+, 시크릿은 Vault. 개발 모드 Vault는 **운영 금지** ([`README.md`](../../README.md)). |
| NFR-S-02 | Actuator는 내부망 CIDR 등으로 제한 (`.cursor/rules/agent-security-platform.mdc`). |
| NFR-O-01 | 로그·메트릭·트레이스에 `tenantId`·`traceId` 상관관계. |
| NFR-Q-01 | TDD, Testcontainers(PostgreSQL/Redis), 보안 회귀 시나리오 (`.cursor/rules/tdd-strict.mdc`). |

---

## 7. 기능 요구사항 (FR) — 요약

우선순위: **P0** 필수, **P1** 중요, **P2** 확장.

### 7.1 테넌트·격리

| ID | 요구사항 | 우선순위 |
|----|----------|----------|
| FR-T-01 | 테넌트 라이프사이클·설정(패스워드·MFA 필수·세션) | P0 |
| FR-T-02 | 운영 테넌트 식별: 서브도메인만, 실패 시 400 | P0 |
| FR-T-03 | JWT `tenant_id` ↔ 컨텍스트 검증, 불일치 403 + 감사 | P0 |
| FR-T-04 | M2M: `X-Tenant-ID` 무시, 토큰 `tenant_id`만 사용 | P0 |

### 7.2 신원·인증

| ID | 요구사항 | 우선순위 |
|----|----------|----------|
| FR-I-01 | Argon2id + DelegatingPasswordEncoder | P0 |
| FR-I-02 | 계정 잠금·강제 로그아웃 | P0 |
| FR-I-03 | TOTP, 시크릿 Transit 암호화, 백업코드 일회성 | P0 |
| FR-I-04 | 신규 기기/위험 시 MFA 강화 | P1 |

### 7.3 OAuth 2.0 / OIDC

| ID | 요구사항 | 우선순위 |
|----|----------|----------|
| FR-O-01 | Discovery, JWKS | P0 |
| FR-O-02 | Authorization Code + PKCE 필수 | P0 |
| FR-O-03 | Client Credentials, `tenant_id` 등 클레임 | P0 |
| FR-O-04 | Introspection, Revocation | P0 |
| FR-O-05 | TTL은 테넌트·클라이언트와 정합 (`DEVELOPMENT_PLAN.md` 6절 기본값 참고) | P1 |

### 7.4 클라이언트·세션·페더레이션·관리·감사

- **FR-C-01~03:** 클라이언트 등록·시크릿·Admin API 전용 변경 (`RegisteredClientRepository.save` 비사용) — P0
- **FR-S-01~03:** Redis 세션, SLO 안전 규칙, 디바이스·GeoIP — P0/P1
- **FR-F-01~04:** SAML/OIDC 페더레이션, 링크, 보안 통제 — P1, 보안 항목은 P0
- **FR-A-01~05:** Admin 체인, 역할, Rate limit, 헤더, CORS, Actuator — P0
- **FR-L-01~04:** 불변 감사, HMAC, 조회 제한, 보존·아카이빙 — P0/P1
- **FR-E-01~02:** Outbox·Redis Streams, 향후 Kafka — P0/P2

(상세 구현 경계는 모듈별 `.cursor/rules/agent-module-*.mdc`를 따른다.)

### 7.5 신규 Epic (제품 차별화·도입 요청 대응)

#### SCIM 2.0 (프로비저닝)

| ID | 요구사항 | 우선순위 |
|----|----------|----------|
| FR-SCIM-01 | SCIM 2.0 API(User/Group) 최소 프로파일: 생성·조회·갱신·비활성 | P1 |
| FR-SCIM-02 | 테넌트·인증: Bearer(Integration Token) 또는 mTLS 등 **서버-투-서버** 통제 | P0 |
| FR-SCIM-03 | Idempotency, pagination, 감사 로그(누가 어떤 프로비저닝을 했는지) | P0 |
| FR-SCIM-04 | Outbound(웹훅) 동기화는 1.x 이후 검토 | P2 |

#### 브랜딩·로그인 UI

| ID | 요구사항 | 우선순위 |
|----|----------|----------|
| FR-UX-01 | 테넌트별 로고·제품명·보조 색상 | P1 |
| FR-UX-02 | 커스텀 도메인(호스트) 연동 시 인증서·테넌트 매핑 | P1 |
| FR-UX-03 | 이용약관·개인정보 링크 노출 위치 | P2 |

#### 조건부 접근

| ID | 요구사항 | 우선순위 |
|----|----------|----------|
| FR-CA-01 | 로그인 컨텍스트: 디바이스 지문(UA)·신뢰 저장소, (선택) IP/Geo | P1 |
| FR-CA-02 | 정책: 신규 디바이스·비정상 위치 시 **step-up** (MFA 재검증) | P1 |
| FR-CA-03 | 관리자가 테넌트별 정책 활성/비활성·임계값 설정 | P2 |

#### WebAuthn (패스키)

| ID | 요구사항 | 우선순위 |
|----|----------|----------|
| FR-WA-01 | 등록·인증 플로우, WebAuthn 크레덴셜 저장(암호화·테넌트 격리) | P2 |
| FR-WA-02 | 복구 플로우(백업 코드·관리자 재설정)와 정책 충돌 방지 | P2 |

---

## 8. 규정 준수·데이터

- 패스워드·감사·TLS·최소 권한 DB 등은 [`DEVELOPMENT_PLAN.md` §7](./DEVELOPMENT_PLAN.md) 및 `.cursor/rules/agent-audit-spec.mdc`와 정합.
- GDPR Article 15 등 데이터 주체 권리: 사용자 활동 타임라인·내보내기 요구사항을 Admin/감사 Epic과 연결한다.
- **데이터 레지던시:** SaaS 모델 제공 시 리전 고정·크로스보더 전송 계약을 PRD 부속서에 둔다.

---

## 9. 로드맵 매핑

[`DEVELOPMENT_PLAN.md`](./DEVELOPMENT_PLAN.md) Phase 0~5와 본 PRD의 P0 요구사항이 대응된다. SCIM·브랜딩·조건부 접근·WebAuthn은 **Phase 5 이후 또는 병렬 Epic**으로 계획한다.

---

## 10. 문서 정합성·추적

| 항목 | 조치 |
|------|------|
| 제품 본문 | 이 파일 [`.cursor/docs/PRD.md`](./PRD.md) |
| 기술 요구·추적 매트릭스 | [`.cursor/docs/TRD.md`](./TRD.md) |
| 구현 규칙 | [`AGENTS.md`](../../AGENTS.md), `.cursor/rules/*.mdc` |
| DDL·운영 SQL | [`.cursor/docs/DATABASE_RULES.md`](./DATABASE_RULES.md) |
| 로드맵·ADR | [`.cursor/docs/DEVELOPMENT_PLAN.md`](./DEVELOPMENT_PLAN.md) |
| 이슈 레이블 | `FR-xxx`, `NFR-xxx`, `TR-xxx`, Epic명 `scim`, `branding`, `conditional-access`, `webauthn` 권장 |

---

## 11. 승인 (초안)

| 역할 | 이름 | 일자 |
|------|------|------|
| 제품 책임자 | | |
| 보안 | | |
| 아키텍처 | | |

---

## 부록 A. 문서 위치

제품·로드맵·DB 규칙·기술 요구사항은 **저장소 기준 `.cursor/docs/`** 에서만 관리한다. (에이전트·IDE 규칙은 `.cursor/rules/*.mdc`, 구현 진입점은 루트 `AGENTS.md`.)
