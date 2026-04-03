# SSO Platform Agent Technical Specification

> 대상 독자: AI 에이전트 (코드 생성 시 이 문서를 참조하여 구현)
> 이 문서에 정의된 규칙은 모든 모듈 구현에 강제 적용된다. 모호한 경우 이 문서를 우선한다.

---

## 0. 프로젝트 컨텍스트

- **루트 경로:** `sso-platform/`
- **그룹:** `com.gitbyul`
- **Java:** 25, **Spring Boot:** 4.0.5, **Gradle:** 9.4.1 (Kotlin DSL)
- **빌드:** 멀티모듈. 루트 `build.gradle.kts` + 각 모듈 `build.gradle.kts`
- **기존 인프라:** PostgreSQL 16, Redis 7, HashiCorp Vault 1.16 (`docker-compose.yml` 참조)
- **실행 모듈:** `sso-bootstrap` (유일한 `@SpringBootApplication` 진입점)
- **라이브러리 모듈:** 나머지 10개 모듈 (`java-library` 플러그인, `@SpringBootApplication` 없음)

**관련 문서 (진입점):** [`README.md`](../README.md) — 실행·Docker. [`DEVELOPMENT_PLAN.md`](./DEVELOPMENT_PLAN.md) — 로드맵·ADR. **PostgreSQL 객체 명명·인덱스·제약조건·모니터링**은 본 문서와 별도로 [`DATABASE_RULES.md`](./DATABASE_RULES.md) §0(산업 참고)·§2(프로젝트 강제)를 따른다. Flyway SQL(§7) 작성 시 해당 규칙과 충돌하지 않을 것.

---

## 1. 패키지 구조 규칙

### 1.1 컨텍스트 모듈 표준 패키지 템플릿

모든 컨텍스트 모듈(`sso-{name}-context`)은 아래 구조를 따른다.

```
com.gitbyul.{name}/
├── command/
│   ├── application/
│   │   ├── usecase/          UseCase 인터페이스 (예: RegisterUserUseCase.java)
│   │   └── handler/          CommandHandler 구현체 (예: RegisterUserCommandHandler.java)
│   ├── domain/
│   │   ├── model/            Aggregate Root, Entity, Value Object
│   │   ├── event/            Domain Event (예: UserRegisteredEvent.java)
│   │   └── repository/       Repository 포트 인터페이스
│   └── infrastructure/
│       ├── persistence/      JPA Entity, JpaRepository, Repository 어댑터
│       └── event/            Domain Event Publisher 구현체
│
├── query/
│   ├── application/
│   │   └── usecase/          Query UseCase 인터페이스 (예: GetUserQuery.java)
│   ├── model/                Read Model DTO (예: UserSummaryDto.java)
│   └── infrastructure/
│       └── repository/       QueryRepository (Native SQL / JOOQ 사용, JPA 금지)
│
└── config/                   모듈 내부 Spring 설정 클래스 (@Configuration)
```

### 1.2 sso-shared-kernel 패키지 구조

```
com.gitbyul.shared/
├── domain/
│   ├── AggregateRoot.java
│   ├── DomainEvent.java
│   ├── ValueObject.java
│   └── EntityId.java
├── audit/
│   └── AuditEvent.java       모든 컨텍스트가 공유하는 감사 이벤트 모델
├── tenant/
│   ├── TenantContextHolder.java
│   ├── TenantResolutionFilter.java
│   └── TenantJwtValidationFilter.java
├── outbox/
│   ├── OutboxEvent.java
│   └── OutboxPoller.java
├── exception/
│   ├── SsoDomainException.java
│   ├── SsoNotFoundException.java
│   └── ErrorCode.java        (enum)
└── util/
    ├── TimeProvider.java
    └── RandomIdGenerator.java
```

### 1.3 네이밍 규칙

| 종류 | 규칙 | 예시 |
|---|---|---|
| Aggregate Root | `{명사}` | `User`, `Tenant`, `OAuthClient` |
| Domain Event | `{과거형동사}Event` | `UserRegisteredEvent`, `TokenIssuedEvent` |
| UseCase 인터페이스 | `{동사}{명사}UseCase` | `RegisterUserUseCase` |
| Command Handler | `{동사}{명사}CommandHandler` | `RegisterUserCommandHandler` |
| Query UseCase | `Get{명사}Query` or `List{명사}Query` | `GetUserQuery` |
| Repository 포트 | `{명사}Repository` | `UserRepository` |
| JPA Repository | `{명사}JpaRepository` | `UserJpaRepository` |
| JPA Entity | `{명사}JpaEntity` | `UserJpaEntity` |
| Repository 어댑터 | `{명사}RepositoryAdapter` | `UserRepositoryAdapter` |
| Read Model DTO | `{명사}SummaryDto` or `{명사}DetailDto` | `UserSummaryDto` |

### 1.4 금지 패턴

- Command 핸들러에서 조회 로직 혼입 금지 (`@Transactional` + SELECT 금지)
- Query 핸들러에서 상태 변경 금지 (반드시 `@Transactional(readOnly = true)`)
- 컨텍스트 모듈 간 직접 의존 금지 (`implementation(project(":other-context"))` 금지)
- JPA Entity를 도메인 레이어에 노출 금지 (Infrastructure 레이어 내부에만 사용)
- `@SpringBootApplication` 은 `sso-bootstrap` 에만 허용
- Query 레이어에서 JPA `@Entity` 기반 조회 금지 (Native Query 또는 JOOQ 사용)

---

## 2. 모듈별 구현 명세

### 2.1 sso-shared-kernel

**build.gradle.kts 최종 의존성:**
```kotlin
plugins { `java-library` }

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("io.micrometer:micrometer-core")
    implementation("com.fasterxml.jackson.core:jackson-databind")
}
```

**구현 클래스 목록:**

| 클래스 | 역할 |
|---|---|
| `AggregateRoot` | 도메인 이벤트 수집 (`List<DomainEvent> domainEvents`) |
| `DomainEvent` | 마커 인터페이스. `eventId()`, `occurredAt()` default 메서드 제공 |
| `ValueObject` | `equals`/`hashCode` 추상 기반 |
| `EntityId` | UUID 래퍼. `of(UUID)`, `generate()` 팩토리 메서드 |
| `TenantContextHolder` | `ThreadLocal<String>` 기반. `get()`, `set()`, `clear()` |
| `TenantResolutionFilter` | `OncePerRequestFilter`. 서브도메인 우선, 헤더 Fallback |
| `TenantJwtValidationFilter` | JWT `tenant_id` 클레임과 `TenantContextHolder` 일치 검증 |
| `MdcPropagationFilter` | `tenantId`, `traceId`, `userId` 를 MDC에 주입 |
| `OutboxEvent` | `@Entity`. `id`, `aggregateType`, `payload`(JSON), `published`, `createdAt` |
| `OutboxPoller` | `@Scheduled`. 미발행 OutboxEvent 조회 → Redis Streams 발행 |
| `SsoDomainException` | `RuntimeException`. `ErrorCode` 포함 |
| `ErrorCode` | enum. `code`, `message`, `httpStatus` 필드 |
| `TimeProvider` | 인터페이스. 기본 구현 `SystemTimeProvider`, 테스트 구현 `FixedTimeProvider` |
| `AuditEvent` | Java record. 섹션 4 참조 |

---

### 2.2 sso-tenant-context

**build.gradle.kts 최종 의존성:**
```kotlin
plugins { `java-library` }

dependencies {
    implementation(project(":sso-shared-kernel"))
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    runtimeOnly("org.postgresql:postgresql")
}
```

**도메인 모델:**

| 클래스 | 종류 | 핵심 필드 |
|---|---|---|
| `Tenant` | AggregateRoot | `tenantId`, `name`, `domain`, `status(ACTIVE/SUSPENDED/DELETED)` |
| `TenantSettings` | ValueObject | `passwordPolicy`, `mfaRequired`, `sessionTimeoutMinutes`, `maxConcurrentSessions` |
| `PasswordPolicy` | ValueObject | `minLength`, `requireUppercase`, `requireNumber`, `requireSpecialChar`, `expiryDays` |

**Flyway 마이그레이션 (tenant 스키마):**
- `V1.0.0__create_tenants_table.sql`
- `V1.0.1__create_tenant_settings_table.sql`

**구현 UseCase:**
- `CreateTenantUseCase` → `TenantCreatedEvent` 발행
- `UpdateTenantStatusUseCase` → `TenantStatusChangedEvent` 발행
- `GetTenantQuery` → `TenantDetailDto` 반환
- `ListTenantsQuery` → `Page<TenantSummaryDto>` 반환

---

### 2.3 sso-identity-context

**build.gradle.kts 최종 의존성:**
```kotlin
plugins { `java-library` }

dependencies {
    implementation(project(":sso-shared-kernel"))
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.bouncycastle:bcprov-jdk18on:1.79")
    runtimeOnly("org.postgresql:postgresql")
}
```

**도메인 모델:**

| 클래스 | 종류 | 핵심 필드 |
|---|---|---|
| `User` | AggregateRoot | `userId`, `tenantId`, `email`, `status(ACTIVE/LOCKED/DEACTIVATED)`, `emailVerified` |
| `Credential` | Entity | `credentialId`, `userId`, `hashedPassword`, `expiresAt`, `failureCount` |
| `MfaEnrollment` | Entity | `enrollmentId`, `userId`, `method(TOTP)`, `secret`, `backupCodes`, `verified` |
| `UserRole` | ValueObject | `role(USER/TENANT_ADMIN/SUPER_ADMIN)` |

**MFA 민감 데이터 저장 규칙:**
- `secret` (TOTP 시크릿): Vault Transit Engine으로 애플리케이션 레벨 암호화 후 DB 저장. 평문 저장 절대 금지. Vault 경로: `transit/keys/sso-mfa-secret`.
- `backupCodes`: 개별 코드를 Argon2id 해시하여 저장. 평문/복호화 가능 형태 금지. 사용된 코드는 해시 비교 후 삭제 처리.
- `secret`, `backupCodes`는 감사 로그 `beforeState`/`afterState`에 절대 포함 금지 (섹션 4.3 민감 필드 처리 규칙 참조).
- MFA 등록 해제 시 DB에서 `secret`, `backupCodes` 물리 삭제 (soft delete 금지).

**Argon2id PasswordEncoder 설정 (config 패키지):**
```java
// PasswordConfig.java
@Bean
public PasswordEncoder passwordEncoder() {
    Map<String, PasswordEncoder> encoders = new HashMap<>();
    encoders.put("argon2", Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8());
    return new DelegatingPasswordEncoder("argon2", encoders);
}
```

**SsoUserDetailsService:**
- `UserDetailsService` 구현체
- `tenantId` + `email`로 사용자 조회 (`TenantContextHolder.get()` 사용)
- 계정 잠금, 이메일 미인증 상태 반영

**Flyway 마이그레이션 (identity 스키마):**
- `V2.0.0__create_users_table.sql`
- `V2.0.1__create_credentials_table.sql`
- `V2.0.2__create_mfa_enrollments_table.sql`

**구현 UseCase:**
- `RegisterUserUseCase` → `UserRegisteredEvent` 발행
- `AuthenticateUserUseCase` → 결과: `AuthenticationResult` (성공/실패 사유)
- `ChangePasswordUseCase` → `PasswordChangedEvent` 발행
- `EnrollMfaUseCase` → `MfaEnrolledEvent` 발행
- `LockUserUseCase` → `UserLockedEvent` 발행 (로그인 실패 5회 초과 시 자동 호출)

---

### 2.4 sso-client-context

**build.gradle.kts 최종 의존성:**
```kotlin
plugins { `java-library` }

dependencies {
    implementation(project(":sso-shared-kernel"))
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.cloud:spring-cloud-starter-vault-config")
    runtimeOnly("org.postgresql:postgresql")
}
```

**도메인 모델:**

| 클래스 | 종류 | 핵심 필드 |
|---|---|---|
| `OAuthClient` | AggregateRoot | `clientId`, `tenantId`, `clientSecretRef`(Vault 경로), `redirectUris`, `scopes`, `grantTypes`, `status` |
| `TokenSettings` | ValueObject | `accessTokenTtl`, `refreshTokenTtl`, `reuseRefreshToken` |
| `ClientGrantType` | Enum | `AUTHORIZATION_CODE`, `CLIENT_CREDENTIALS`, `REFRESH_TOKEN` |

**SpringRegisteredClientAdapter:**
- `RegisteredClientRepository` 인터페이스 구현
- `OAuthClient` → Spring Authorization Server의 `RegisteredClient` 변환
- 클라이언트 시크릿은 Vault Transit에서 복호화 후 반환

**Flyway 마이그레이션 (client 스키마):**
- `V3.0.0__create_oauth_clients_table.sql`
- `V3.0.1__create_client_redirect_uris_table.sql`
- `V3.0.2__create_client_scopes_table.sql`

**구현 UseCase:**
- `RegisterClientUseCase` → `ClientRegisteredEvent` 발행
- `RotateClientSecretUseCase` → Vault 새 시크릿 생성 → `ClientSecretRotatedEvent` 발행
- `DeactivateClientUseCase`

---

### 2.5 sso-authorization-context

**build.gradle.kts 최종 의존성:**
```kotlin
plugins { `java-library` }

dependencies {
    implementation(project(":sso-shared-kernel"))
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-authorization-server")
    implementation("org.springframework.boot:spring-boot-starter-validation")
}
```

**SecurityFilterChain 구성 (반드시 @Order 명시):**

`sso-authorization-context`는 Order=1(Authorization Server)과 Order=3(Resource Server)만 정의한다.
Order=2(Admin)의 실제 `SecurityFilterChain` Bean 정의는 `sso-admin-context`에서 담당한다.

```java
// Order=1: Spring Authorization Server FilterChain (sso-authorization-context 소유)
// 경로: /oauth2/**, /.well-known/**, /userinfo
@Bean @Order(1)
public SecurityFilterChain authorizationServerFilterChain(HttpSecurity http) { ... }

// Order=2: Admin FilterChain (sso-admin-context 소유 — 여기서는 참조만)
// 경로: /admin/**
// 인증: JWT Bearer + ROLE_SUPER_ADMIN 또는 ROLE_TENANT_ADMIN
// Admin 전용 JWT만 허용 (일반 사용자 토큰 거부)

// Order=3: Resource Server FilterChain (sso-authorization-context 소유)
// 경로: /api/**
// 인증: JWT Bearer Token (JWK 검증)
@Bean @Order(3)
public SecurityFilterChain resourceServerFilterChain(HttpSecurity http) { ... }
```

**역할 체계 (전체 시스템 공통):**

| 역할 | 범위 | Admin 접근 |
|---|---|---|
| `ROLE_SUPER_ADMIN` | 전체 테넌트 접근 | 허용 |
| `ROLE_TENANT_ADMIN` | 자신의 테넌트만 접근 | 허용 (테넌트 격리) |
| `ROLE_USER` | 일반 사용자 | 불가 |

> **폐기:** `ROLE_ADMIN` 명칭은 사용하지 않는다. 모든 코드에서 `ROLE_SUPER_ADMIN` 또는 `ROLE_TENANT_ADMIN`을 사용할 것.

**TenantAwareTokenCustomizer (OAuth2TokenCustomizer 구현):**

Access Token 및 ID Token에 추가할 클레임:

| 클레임 키 | 값 | 출처 |
|---|---|---|
| `tenant_id` | 현재 테넌트 ID | `TenantContextHolder.get()` |
| `roles` | 사용자 역할 목록 | `UserDetailsService` |
| `mfa_verified` | MFA 인증 여부 | 인증 컨텍스트 |

**Issuer URI 패턴:**
- 서브도메인 방식: `https://{tenantId}.sso.company.com`
- `ProviderSettings`의 `issuer`를 테넌트별로 동적 설정

**Flyway 마이그레이션 (Spring Authorization Server 공식 DDL):**
- `V4.0.0__create_spring_authorization_server_tables.sql`
- 공식 DDL 파일 경로: `spring-authorization-server` JAR 내 `oauth2-authorization-schema.sql`
- 스키마 접두사: `client.oauth2_registered_client`, `client.oauth2_authorization`, `client.oauth2_authorization_consent`

---

### 2.6 sso-key-context

**build.gradle.kts 최종 의존성:**
```kotlin
plugins { `java-library` }

dependencies {
    implementation(project(":sso-shared-kernel"))
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.cloud:spring-cloud-starter-vault-config")
    implementation("com.nimbusds:nimbus-jose-jwt:9.40")
}
```

**도메인 모델:**

| 클래스 | 종류 | 핵심 필드 |
|---|---|---|
| `SigningKey` | AggregateRoot | `keyId`, `algorithm(RS256/ES256)`, `vaultPath`, `status(ACTIVE/ROTATING/REVOKED)`, `expiresAt` |
| `KeyRotationPolicy` | ValueObject | `rotationIntervalDays`, `overlapDays` (이전 키 유효 기간 중첩) |

**VaultKeyStore:**
- Vault Transit Engine을 사용하여 개인키 저장 및 서명 수행
- Vault 경로 패턴: `transit/keys/sso-signing-{keyId}`
- 공개키만 JWK로 노출

**JwkSetProvider:**
- `JWKSource<SecurityContext>` 구현체
- ACTIVE 상태 키의 공개키를 JWK Set으로 반환
- Redis 캐시 적용 (TTL: 5분)

**Flyway 마이그레이션:**
- `V6.0.0__create_key_metadata_table.sql` (key 스키마)

**공개 엔드포인트:**
- `GET /.well-known/jwks.json` — 인증 불필요, 공개

---

### 2.7 sso-session-context

**build.gradle.kts 최종 의존성:**
```kotlin
plugins { `java-library` }

dependencies {
    implementation(project(":sso-shared-kernel"))
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.session:spring-session-data-redis")
    implementation("com.maxmind.geoip2:geoip2:4.2.0")
}
```

**도메인 모델:**

| 클래스 | 종류 | 핵심 필드 |
|---|---|---|
| `SsoSession` | AggregateRoot | `sessionId`, `userId`, `tenantId`, `clientSessions(List)`, `loginAt`, `lastAccessAt`, `expiresAt` |
| `ClientSession` | ValueObject | `clientId`, `logoutUri`, `accessTokenJti` |
| `DeviceInfo` | ValueObject | `ipAddress`, `userAgent`, `deviceFingerprint`, `geoCountry`, `geoCity` |

**Redis 키 패턴:**
- SSO 세션: `sso:session:{sessionId}` (TTL: tenantSettings.sessionTimeoutMinutes)
- 사용자별 세션 인덱스: `sso:user-sessions:{tenantId}:{userId}` (Set 타입)

**SLO (Single Logout) 구현:**
- `PropagateLogoutUseCase`: 세션에 연결된 모든 `ClientSession`의 `logoutUri`로 백채널 로그아웃 요청 전송
- HTTP POST, `logout_token` 파라미터 포함 (OpenID Connect Back-Channel Logout 표준)

**SLO 백채널 보안 규칙:**
- **SSRF 방지:** `logoutUri`는 `https` 스킴만 허용한다. 사설 IP 대역(`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`, `127.0.0.0/8`), 링크 로컬(`169.254.0.0/16`), 클라우드 메타데이터 URL(`169.254.169.254`) 접근을 차단한다.
- **URI 사전 검증:** OAuthClient 등록(`RegisterClientUseCase`) 시 `logoutUri`의 스킴·호스트를 검증하고 등록된 URI만 SLO 요청 대상으로 허용한다. 런타임에 미등록 URI로의 요청은 즉시 거부.
- **로그아웃 토큰 서명:** 백채널 로그아웃 토큰(`logout_token`)은 `sso-key-context`의 ACTIVE 서명 키로 JWT 서명하여 전송한다.
- **타임아웃:** 아웃바운드 HTTP 요청의 connect timeout 3초, read timeout 5초. 실패 시 1회 재시도 후 `AUTHENTICATION.LOGOUT` 감사 이벤트에 `failureReason` 기록.
- **비동기 처리:** SLO 전파는 `@Async`로 실행하여 사용자 로그아웃 응답 지연을 방지한다.

**Flyway 마이그레이션:**
- `V5.0.0__create_session_metadata_table.sql` (session 스키마 — 세션 통계용, 실제 세션은 Redis)

---

### 2.8 sso-audit-context

**build.gradle.kts 최종 의존성:**
```kotlin
plugins { `java-library` }

dependencies {
    implementation(project(":sso-shared-kernel"))
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("io.micrometer:micrometer-core")
    runtimeOnly("org.postgresql:postgresql")
}
```

**AuditEventPersistenceHandler:**
- `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` 사용
- `@Async` 적용 (감사 로그 실패가 도메인 트랜잭션에 영향 없도록)
- 예외 발생 시 로그 기록 후 무시 (절대 예외 전파 금지)
- Checksum(HMAC-SHA256) 계산 후 함께 저장
- **실패 메트릭:** 예외 catch 후 반드시 `sso.audit.persistence.failures` Counter 메트릭을 증가시킨다.
- **연속 실패 알림:** 연속 10회 이상 저장 실패 시 `SECURITY.AUDIT_PIPELINE_DEGRADED` 이벤트를 **동기적으로** 별도 경로(파일 로그 / stderr)에 기록한다 (DB 장애 상황에서도 감사 침묵 방지).

**SecurityAnomalyDetector (Redis Streams Consumer):**
- 스트림 채널: `sso:stream:security-events`
- Brute Force 감지: 10분 내 동일 IP 로그인 실패 10회 이상
- Impossible Travel 감지: 같은 userId로 5분 내 다른 국가 로그인
- 탐지 시: `SECURITY.BRUTE_FORCE_DETECTED` 또는 `SECURITY.IMPOSSIBLE_TRAVEL` AuditEvent 생성

**AuditQueryController (Query Side):**
- `@Transactional(readOnly = true)`
- Native SQL 사용 (JPA Query 금지 — 복잡 필터링)
- 응답은 스트리밍 (`StreamingResponseBody`) — 대용량 내보내기
- **접근 권한:** `ROLE_SUPER_ADMIN`은 전체 테넌트 감사 로그 조회 가능. `ROLE_TENANT_ADMIN`은 자기 테넌트 감사 로그만 조회 가능 (쿼리에 `tenantId` 필터 강제 적용). `ROLE_USER`는 접근 불가.
- **Rate limit:** 스트리밍 다운로드 요청은 관리자 1인당 분당 5회로 제한한다.

**Flyway 마이그레이션:**
- `V7.0.0__create_audit_events_partitioned_table.sql`
- `V7.0.1__create_audit_events_indexes.sql`
- `V7.0.2__add_audit_immutability_rules.sql` (DELETE/UPDATE Rule 및 권한 설정)

---

### 2.9 sso-federation-context

**build.gradle.kts 최종 의존성:**
```kotlin
plugins { `java-library` }

dependencies {
    implementation(project(":sso-shared-kernel"))
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.security:spring-security-saml2-service-provider")
    implementation("org.springframework.boot:spring-boot-starter-validation")
}
```

**도메인 모델:**

| 클래스 | 종류 | 핵심 필드 |
|---|---|---|
| `IdentityProvider` | AggregateRoot | `providerId`, `tenantId`, `type(SAML/OIDC)`, `status`, `attributeMappings` |
| `FederatedIdentity` | Entity | `externalSubject`, `providerId`, `internalUserId`, `linkedAt` |
| `AttributeMapping` | ValueObject | `externalClaim`, `internalClaim` (예: `email` → `email`) |

**페더레이션 보안 규칙:**

- **OIDC 연동:**
  - `state`, `nonce` 파라미터 필수 생성 및 검증. 누락 시 인증 흐름 거부.
  - ID Token 검증: `iss`(발급자), `aud`(대상), `exp`(만료), `nonce` 필수 확인.
  - 동일 `nonce`로 ID Token 재제출 시 거부 (리플레이 공격 방지).
- **SAML 2.0 연동:**
  - `InResponseTo` 속성 검증 필수 (요청과 응답 매핑).
  - Assertion XML 서명 검증 필수. 서명 없는 Assertion 거부.
  - Clock skew 허용치: 최대 120초. `NotBefore`, `NotOnOrAfter` 조건 검증.
- **IdP 메타데이터 관리:**
  - 최초 등록 시 IdP 인증서 핀닝 (fingerprint 저장).
  - 메타데이터 자동 갱신 주기: 24시간.
  - 인증서 변경 감지 시 `FEDERATION.IDP_CERT_ROTATED` 감사 이벤트 발행 + 관리자 알림.
- **계정 링크 보안:**
  - `FederatedIdentity` 최초 링크 시: 기존 로그인된 세션 재인증 필수.
  - 이메일 기반 자동 링크 시: 이메일 소유권 검증 완료된 내부 계정만 대상 (계정 탈취 완화).
  - 링크 해제 시 `FEDERATION.IDP_UNLINKED` 감사 이벤트 발행.

**Flyway 마이그레이션:**
- `V8.0.0__create_identity_providers_table.sql`
- `V8.0.1__create_federated_identities_table.sql`

---

### 2.10 sso-admin-context

**build.gradle.kts 최종 의존성:**
```kotlin
plugins { `java-library` }

dependencies {
    implementation(project(":sso-shared-kernel"))
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
}
```

**Admin SecurityFilterChain 규칙:**
- 경로: `/admin/**`
- 필수 권한: `ROLE_SUPER_ADMIN` (전체 접근) 또는 `ROLE_TENANT_ADMIN` (자신의 테넌트만)
- 별도 JWT 검증 (Admin 전용 발급 토큰, 일반 사용자 토큰 거부)

---

### 2.11 sso-bootstrap

**역할:** 실행 진입점. 비즈니스 로직 없음. 모든 컨텍스트 모듈 조합만 수행.

**추가할 의존성 (기존 유지 + 추가):**
```kotlin
// 기존 유지
implementation("org.springframework.boot:spring-boot-starter-actuator")
implementation("org.springframework.boot:spring-boot-starter-webmvc")

// 추가
implementation("io.micrometer:micrometer-registry-prometheus")
implementation("io.micrometer:micrometer-tracing-bridge-otel")
implementation("io.opentelemetry:opentelemetry-exporter-otlp")
implementation("net.logstash.logback:logstash-logback-encoder:8.0")
implementation("org.flywaydb:flyway-core")
implementation("org.flywaydb:flyway-database-postgresql")
implementation("org.springframework.cloud:spring-cloud-starter-vault-config")
```

**Flyway 마이그레이션 파일 위치:**
```
sso-bootstrap/src/main/resources/db/migration/
├── V1.0.0__create_tenants_table.sql
├── V1.0.1__create_tenant_settings_table.sql
├── V2.0.0__create_users_table.sql
├── V2.0.1__create_credentials_table.sql
├── V2.0.2__create_mfa_enrollments_table.sql
├── V3.0.0__create_oauth_clients_table.sql
├── V3.0.1__create_client_redirect_uris_table.sql
├── V3.0.2__create_client_scopes_table.sql
├── V4.0.0__create_spring_authorization_server_tables.sql
├── V5.0.0__create_session_metadata_table.sql
├── V6.0.0__create_key_metadata_table.sql
├── V7.0.0__create_audit_events_partitioned_table.sql
├── V7.0.1__create_audit_events_indexes.sql
├── V7.0.2__add_audit_immutability_rules.sql
├── V8.0.0__create_identity_providers_table.sql
└── V8.0.1__create_federated_identities_table.sql
```

---

## 3. 공통 구현 패턴

### 3.1 CQRS Command/Query 분리 규칙

**Command UseCase:**
```java
// 반환값: ID(EntityId) 또는 void 만 허용
// 조회 로직 혼입 금지
public interface RegisterUserUseCase {
    UserId execute(RegisterUserCommand command);
}
```

**Query UseCase:**
```java
// 반환값: DTO 또는 Page<DTO>
// @Transactional(readOnly = true) 필수
// 상태 변경 절대 금지
public interface GetUserQuery {
    UserDetailDto execute(String userId, String tenantId);
}
```

**Command 객체:** Java record 사용. `@NotNull`, `@NotBlank` 등 Bean Validation 적용.

**Query 조회:** Native SQL 사용 (JOOQ 권장). `EntityManager.createNativeQuery()` 또는 JOOQ DSL.

---

### 3.2 @TransactionalEventListener 패턴

```java
// 도메인 트랜잭션 커밋 이후에만 실행 보장
// @Async: 감사 로그 실패가 도메인에 영향 없도록 격리
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
@Async
public void on(UserRegisteredEvent event) {
    try {
        // 감사 이벤트 처리
    } catch (Exception e) {
        log.error("Audit event processing failed", e);
        // 예외 전파 금지
    }
}
```

---

### 3.3 Transactional Outbox Pattern

**Outbox 테이블 DDL:**
```sql
CREATE TABLE shared.outbox_events (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id  VARCHAR(100) NOT NULL,
    event_type    VARCHAR(100) NOT NULL,
    payload       JSONB       NOT NULL,
    published     BOOLEAN     NOT NULL DEFAULT false,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at  TIMESTAMPTZ
);

CREATE INDEX idx_outbox_unpublished ON shared.outbox_events (created_at)
    WHERE published = false;
```

**발행 흐름:**
1. Command 트랜잭션 내에서 도메인 테이블 변경 + `outbox_events` INSERT (같은 트랜잭션)
2. `OutboxPoller` (`@Scheduled(fixedDelay = 1000)`) 가 `published = false` 이벤트 조회
3. Redis Streams (`XADD`) 발행 성공 시 `published = true` 업데이트

---

### 3.4 TenantContextHolder + TenantResolutionFilter

```java
// TenantResolutionFilter: 요청마다 실행
// 서브도메인 추출 → 헤더 Fallback → TenantContextHolder 저장 → MDC 주입
public class TenantResolutionFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) {
        String tenantId = extractFromSubdomain(request);
        if (tenantId == null) {
            tenantId = request.getHeader("X-Tenant-ID");
        }
        if (tenantId == null || !isValidTenant(tenantId)) {
            response.sendError(400, "Tenant not identified");
            return;
        }
        TenantContextHolder.set(tenantId);
        MDC.put("tenantId", tenantId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
            MDC.remove("tenantId");
        }
    }
}
```

**서브도메인 추출 규칙:**
- `request.getServerName()` 에서 첫 번째 서브도메인 세그먼트 추출
- 로컬/개발 환경 (`localhost`, IP 주소): 헤더만 사용
- 예외 경로 (`/actuator/**`, `/.well-known/**`): 테넌트 필터 스킵

**`X-Tenant-ID` 헤더 프로파일별 제한 규칙:**
- **운영(production) 프로파일:** 서브도메인 추출 실패 시 `X-Tenant-ID` 헤더 Fallback 사용 금지. 서브도메인에서 테넌트를 식별할 수 없으면 즉시 `400 Bad Request` 반환.
- **개발/로컬(local, dev) 프로파일:** `X-Tenant-ID` 헤더 Fallback 허용 (기존 동작 유지).
- **M2M(서버 간) 요청:** `client_credentials` 그랜트로 발급된 토큰의 `tenant_id` 클레임으로만 테넌트를 식별한다. `X-Tenant-ID` 헤더 무시.

**JWT-테넌트 바인딩 검증 규칙:**
- 인증 완료 후, JWT의 `tenant_id` 클레임과 `TenantContextHolder.get()` 값이 **반드시 일치**해야 한다.
- 불일치 시 `403 Forbidden` 반환 + `SECURITY.TENANT_MISMATCH` 감사 이벤트 발행.
- 검증 위치: `resourceServerFilterChain`(Order=3) 및 `adminFilterChain`(Order=2)의 JWT 인증 필터 이후 커스텀 `TenantJwtValidationFilter`에서 수행.
- 구현 클래스: `com.gitbyul.shared.tenant.TenantJwtValidationFilter` (`sso-shared-kernel`에 위치).

---

### 3.5 DelegatingPasswordEncoder (Argon2id) 설정

```java
@Bean
public PasswordEncoder passwordEncoder() {
    Map<String, PasswordEncoder> encoders = new HashMap<>();
    // 현재 기본 인코더
    Argon2PasswordEncoder argon2 = new Argon2PasswordEncoder(
        16,     // saltLength
        32,     // hashLength
        1,      // parallelism
        65536,  // memory (KB) = 64MB
        3       // iterations
    );
    encoders.put("argon2", argon2);
    // 레거시 호환 (마이그레이션용, 신규 해시 생성 금지)
    encoders.put("bcrypt", new BCryptPasswordEncoder());

    return new DelegatingPasswordEncoder("argon2", encoders);
    // 저장 형태: {argon2}$argon2id$v=19$m=65536,t=3,p=1$...
}
```

---

## 4. 감사 로그 명세

### 4.1 AuditEvent 공통 필드 (Java record)

```java
public record AuditEvent(
    UUID    eventId,           // UUID v7 (시간순 정렬 가능)
    String  eventType,         // "AUTHENTICATION.LOGIN_SUCCESS" 형식
    String  eventVersion,      // "1.0" (스키마 버전)
    Instant occurredAt,        // 이벤트 발생 시각 (UTC)
    Instant recordedAt,        // DB 기록 시각
    String  tenantId,          // 필수
    String  tenantDomain,      // 예: "kakaobank.sso.company.com"
    String  actorType,         // "USER" | "SYSTEM" | "ADMIN" | "SERVICE"
    String  actorId,
    String  actorEmail,        // 마스킹 적용: user****@domain.com
    String  actorIp,           // IPv4/IPv6
    String  targetType,        // "USER" | "CLIENT" | "TOKEN" | "SESSION"
    String  targetId,
    String  result,            // "SUCCESS" | "FAILURE" | "PARTIAL"
    String  failureReason,     // null if SUCCESS
    String  traceId,           // OpenTelemetry TraceID (MDC에서 추출)
    String  sessionId,
    String  clientId,
    String  userAgent,
    String  deviceFingerprint, // SHA-256(IP + UserAgent + 기타)
    String  geoCountry,        // ISO 3166-1 alpha-2
    String  geoCity,
    JsonNode beforeState,      // 변경 전 (민감 필드 제거 후)
    JsonNode afterState,       // 변경 후 (민감 필드 제거 후)
    Map<String, String> metadata, // 이벤트 타입별 추가 데이터
    String  checksum           // HMAC-SHA256(eventId + eventType + occurredAt + actorId + targetId + result)
)
```

### 4.2 이벤트 타입 전체 목록

```
AUTHENTICATION.LOGIN_SUCCESS
AUTHENTICATION.LOGIN_FAILURE
AUTHENTICATION.LOGOUT
AUTHENTICATION.TOKEN_ISSUED
AUTHENTICATION.TOKEN_REFRESHED
AUTHENTICATION.TOKEN_REVOKED
AUTHENTICATION.TOKEN_INTROSPECTED
AUTHENTICATION.SESSION_EXPIRED

MFA.MFA_CHALLENGE_SENT
MFA.MFA_CHALLENGE_SUCCESS
MFA.MFA_CHALLENGE_FAILURE
MFA.MFA_ENROLLED
MFA.MFA_UNENROLLED
MFA.MFA_BYPASS_ATTEMPTED

CREDENTIAL.PASSWORD_CHANGED
CREDENTIAL.PASSWORD_RESET_REQUESTED
CREDENTIAL.PASSWORD_RESET_COMPLETED
CREDENTIAL.PASSWORD_EXPIRED
CREDENTIAL.ACCOUNT_LOCKED

AUTHORIZATION.CONSENT_GRANTED
AUTHORIZATION.CONSENT_REVOKED
AUTHORIZATION.SCOPE_DENIED
AUTHORIZATION.PRIVILEGE_ESCALATION_ATTEMPTED

IDENTITY.USER_REGISTERED
IDENTITY.USER_UPDATED
IDENTITY.USER_DEACTIVATED
IDENTITY.USER_DELETED
IDENTITY.EMAIL_VERIFIED

CLIENT.CLIENT_REGISTERED
CLIENT.CLIENT_UPDATED
CLIENT.CLIENT_SECRET_ROTATED
CLIENT.CLIENT_DEACTIVATED
CLIENT.CLIENT_DELETED

TENANT.TENANT_CREATED
TENANT.TENANT_UPDATED
TENANT.TENANT_SUSPENDED
TENANT.TENANT_SETTINGS_CHANGED

KEY_MANAGEMENT.KEY_GENERATED
KEY_MANAGEMENT.KEY_ROTATED
KEY_MANAGEMENT.KEY_REVOKED

FEDERATION.IDP_LINKED
FEDERATION.IDP_LOGIN_SUCCESS
FEDERATION.IDP_LOGIN_FAILURE
FEDERATION.IDP_UNLINKED
FEDERATION.IDP_CERT_ROTATED

SECURITY.SUSPICIOUS_IP_DETECTED
SECURITY.RATE_LIMIT_EXCEEDED
SECURITY.BRUTE_FORCE_DETECTED
SECURITY.IMPOSSIBLE_TRAVEL
SECURITY.TOKEN_REUSE_ATTACK
SECURITY.ANOMALY_DETECTED
SECURITY.TENANT_MISMATCH
SECURITY.AUDIT_PIPELINE_DEGRADED
```

### 4.3 민감 필드 처리 규칙

| 데이터 | 처리 방법 |
|---|---|
| 패스워드 (평문, 해시 모두) | 감사 로그에 절대 포함 금지 |
| 전체 토큰 값 | 금지. `jti` (JWT ID) 만 `metadata`에 저장 |
| 이메일 전체 | 마스킹: `user****@domain.com` (`actorEmail` 필드) |
| IP 주소 | `actorIp` 필드에 전체 저장 (법적 요건), GeoIP 변환 결과도 저장 |
| MFA 시크릿 / 백업 코드 | 감사 로그에 절대 포함 금지 (`beforeState`/`afterState` 포함) |
| `beforeState` / `afterState` | 패스워드 해시, 토큰 시크릿, MFA 시크릿 필드 제거 후 저장 |

### 4.4 Checksum 계산 방식 (HMAC-SHA256)

DB 접근 권한만으로는 checksum을 재계산할 수 없도록 **HMAC-SHA256**을 사용한다.

```java
// checksum = HMAC-SHA256(key, eventId + "|" + eventType + "|" + occurredAt + "|" + actorId + "|" + targetId + "|" + result)
String input = String.join("|",
    eventId.toString(),
    eventType,
    occurredAt.toString(),
    Objects.toString(actorId, ""),
    Objects.toString(targetId, ""),
    result
);
String checksum = HmacUtils.hmacSha256Hex(auditHmacKey, input);
```

**HMAC 키 관리:**
- `auditHmacKey`는 Vault KV에 저장한다. 경로: `secret/sso/audit-hmac-key`.
- 애플리케이션 기동 시 Vault에서 1회 로드 후 메모리에만 보관. 로그·환경변수 노출 금지.
- 키 로테이션 절차: 신규 키 생성 → 이전 키로 기존 checksum 검증 → 신규 키로 재서명하는 배치 마이그레이션 실행 → 이전 키 폐기.
- Vault 접근 불가 시 애플리케이션 기동 실패 (fail-fast).

---

## 5. Spring Authorization Server 연동 명세

### 5.1 다중 SecurityFilterChain 전체 경로 매핑

| Order | FilterChain | 허용 경로 | 인증 방식 |
|---|---|---|---|
| 1 | Authorization Server | `/oauth2/**`, `/.well-known/**`, `/userinfo` | OAuth2 프로토콜 |
| 2 | Admin (`sso-admin-context` 소유) | `/admin/**` | JWT Bearer (`ROLE_SUPER_ADMIN` / `ROLE_TENANT_ADMIN`) |
| 3 | Resource Server | `/api/**` | JWT Bearer Token |
| - | 공개 | `/actuator/health`, `/error` | 없음 |
| - | 차단 | `/actuator/**` (health 제외) | 내부망 IP 필터 |

### 5.2 RegisteredClientRepository 어댑터 구현

- `sso-client-context` 모듈의 `SpringRegisteredClientAdapter`가 구현
- `findByClientId(String clientId)`: `OAuthClient` 조회 → `RegisteredClient` 변환
- `save(RegisteredClient)`: 사용 금지 (클라이언트 등록은 Admin API로만)
- Vault에서 시크릿 복호화 시 예외 발생 → `ClientAuthenticationException` 전환

### 5.3 OAuth2TokenCustomizer 추가 클레임

```java
// Access Token 추가 클레임
context.getClaims().claim("tenant_id", tenantId);
context.getClaims().claim("roles", userRoles);

// ID Token 추가 클레임 (OIDC)
context.getClaims().claim("tenant_id", tenantId);
context.getClaims().claim("mfa_verified", mfaVerified);
context.getClaims().claim("amr", authMethodReferences); // ["pwd", "otp"]
```

### 5.4 PKCE 강제 설정

모든 `authorization_code` 그랜트 타입에 PKCE 필수:
```java
.clientSettings(ClientSettings.builder()
    .requireProofKey(true)         // PKCE 필수
    .requireAuthorizationConsent(true)
    .build())
```

---

## 6. 이벤트 스트림 명세

### 6.1 Redis Streams 채널 이름 규칙

| 채널 | 용도 | Consumer Group |
|---|---|---|
| `sso:stream:authentication-events` | 인증 관련 이벤트 | `audit-consumer-group` |
| `sso:stream:identity-events` | 사용자 계정 변경 | `audit-consumer-group` |
| `sso:stream:security-events` | 보안 이상 감지 대상 | `security-detector-group`, `audit-consumer-group` |
| `sso:stream:key-events` | 키 로테이션 | `audit-consumer-group` |
| `sso:stream:dead-letter` | 처리 실패 이벤트 | `dlq-handler-group` |

### 6.2 Redis Streams 메시지 필드

```
XADD sso:stream:authentication-events * \
  eventId       <uuid> \
  eventType     AUTHENTICATION.LOGIN_SUCCESS \
  tenantId      kakaobank \
  actorId       <userId> \
  occurredAt    <ISO8601> \
  payload       <JSON 직렬화된 AuditEvent>
```

### 6.3 Consumer 구현 패턴

```java
// 컨슈머는 @Scheduled 또는 별도 스레드로 XREADGROUP 호출
// 처리 성공: XACK
// 처리 실패 3회 이상: Dead Letter 채널로 이동
@Scheduled(fixedDelay = 500)
public void consume() {
    List<MapRecord<String, Object, Object>> messages =
        redisTemplate.opsForStream().read(
            Consumer.from("audit-consumer-group", instanceId),
            StreamReadOptions.empty().count(100),
            StreamOffset.create("sso:stream:authentication-events", ReadOffset.lastConsumed())
        );
    // 처리 후 XACK
}
```

---

## 7. Flyway 버전 관리 규칙

테이블·컬럼·뷰·**인덱스·PK/UK/FK/Check 제약조건 명명**, 운영 모니터링 관점은 [`DATABASE_RULES.md`](./DATABASE_RULES.md) §0·§2를 따른다. 본 섹션은 **버전 번호·파일 위치·마이그레이션 절차**에 한정한다.

### 7.1 버전 네이밍

```
V{컨텍스트번호}.{기능번호}.{수정번호}__{설명}.sql

예: V2.0.0__create_users_table.sql
    V2.1.0__add_users_last_login_column.sql
    V2.1.1__fix_users_email_index.sql
```

### 7.2 컨텍스트별 버전 번호 할당

| 컨텍스트 | 버전 대역 |
|---|---|
| shared (outbox 등) | V0.x.x |
| sso-tenant-context | V1.x.x |
| sso-identity-context | V2.x.x |
| sso-client-context | V3.x.x |
| sso-authorization-context | V4.x.x |
| sso-session-context | V5.x.x |
| sso-key-context | V6.x.x |
| sso-audit-context | V7.x.x |
| sso-federation-context | V8.x.x |

### 7.3 마이그레이션 규칙

- 테이블·컬럼·뷰·`CREATE INDEX` / `CREATE UNIQUE INDEX` / 기타 제약조건 명명: [`DATABASE_RULES.md`](./DATABASE_RULES.md) §0(산업 참고)·§2(프로젝트 강제) 준수
- 한 번 적용된 마이그레이션 파일 수정 금지 (Flyway checksum 오류 발생)
- 컬럼 추가: 새 버전 파일 추가
- 컬럼 삭제: 반드시 이전 버전에서 애플리케이션 코드 제거 후 별도 마이그레이션
- 모든 `CREATE TABLE`에 `IF NOT EXISTS` 추가
- 모든 테이블에 `created_at TIMESTAMPTZ NOT NULL DEFAULT now()` 포함

---

## 8. 모니터링 명세

### 8.1 커스텀 메트릭 이름 및 태그

| 메트릭 이름 | 타입 | 필수 태그 | 설명 |
|---|---|---|---|
| `sso.auth.attempts` | Counter | `tenant`, `result(success/failure)`, `mfa_used` | 인증 시도 수 |
| `sso.auth.duration` | Timer | `tenant` | 인증 처리 시간 (p50/p95/p99) |
| `sso.token.issued` | Counter | `tenant`, `grant_type`, `token_type` | 토큰 발급 수 |
| `sso.token.revoked` | Counter | `tenant`, `reason` | 토큰 폐기 수 |
| `sso.session.active` | Gauge | `tenant` | 활성 세션 수 |
| `sso.mfa.challenges` | Counter | `tenant`, `method`, `result` | MFA 챌린지 수 |
| `sso.login.failures` | Counter | `tenant`, `reason` | 로그인 실패 (사유별) |
| `sso.key.rotations` | Counter | `algorithm` | 키 로테이션 횟수 |
| `sso.rate_limit.exceeded` | Counter | `tenant`, `endpoint` | Rate Limit 초과 |
| `sso.audit.persistence.failures` | Counter | `tenant`, `event_type` | 감사 로그 저장 실패 |

### 8.2 Actuator 설정 규칙

```properties
# Actuator 포트 반드시 8081로 분리
management.server.port=8081

# 노출 엔드포인트
management.endpoints.web.exposure.include=health,info,prometheus,metrics,loggers

# Prometheus 활성화
management.prometheus.metrics.export.enabled=true

# 공통 메트릭 태그
management.metrics.tags.application=sso-platform
management.metrics.tags.environment=${spring.profiles.active:local}
```

### 8.3 MDC 키 이름 표준

모든 컨텍스트 모듈에서 아래 MDC 키만 사용. 임의 추가 금지.

| MDC 키 | 주입 위치 | 설명 |
|---|---|---|
| `traceId` | OpenTelemetry 자동 | 분산 추적 ID |
| `spanId` | OpenTelemetry 자동 | 스팬 ID |
| `tenantId` | `TenantResolutionFilter` | 현재 테넌트 |
| `userId` | 인증 성공 후 `MdcPropagationFilter` | 현재 사용자 |
| `clientId` | OAuth2 토큰 요청 시 | OAuth2 클라이언트 |

### 8.4 JSON 로그 구조 (docker/production 프로파일)

```json
{
  "timestamp": "2026-04-03T10:15:30.123Z",
  "level": "INFO",
  "logger": "com.gitbyul.identity.command.handler.AuthenticateUserCommandHandler",
  "message": "사용자 인증 성공",
  "traceId": "abc123def456",
  "spanId": "7890abcd",
  "tenantId": "kakaobank",
  "userId": "user-uuid",
  "clientId": "client-uuid"
}
```

Logback 설정 파일: `sso-bootstrap/src/main/resources/logback-spring.xml`
docker/production 프로파일에서만 JSON 인코더 활성화. local 프로파일은 일반 텍스트 유지.

---

## 9. 테스트 전략

### 9.1 계층별 테스트 범위

| 계층 | 프레임워크 | 범위 |
|---|---|---|
| 도메인 단위 테스트 | JUnit 5 | Aggregate Root 비즈니스 로직 100% |
| 애플리케이션 단위 테스트 | JUnit 5 + Mockito | UseCase/Handler (포트 Mock) |
| 통합 테스트 | Testcontainers (PostgreSQL, Redis) | Repository 어댑터, 이벤트 파이프라인 |
| Spring Security 테스트 | `@WithMockUser`, MockMvc | SecurityFilterChain 경로 검증 |
| OAuth2 E2E 테스트 | Testcontainers + `WebTestClient` | Authorization Code Flow 전체 |

### 9.2 Testcontainers 설정

```java
// 통합 테스트 공통 베이스 클래스
@Testcontainers
@SpringBootTest
public abstract class IntegrationTestBase {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379);
}
```

### 9.3 테스트 금지 패턴

- `H2` 인메모리 DB 사용 금지 (PostgreSQL 전용 DDL 사용으로 호환 불가)
- `@SpringBootTest`에서 실제 Vault 연동 금지 → MockBean 사용
- 감사 로그 테스트에서 실제 HMAC-SHA256 검증 포함 필수

### 9.4 보안 회귀 테스트 (Security Regression Tests)

아래 시나리오는 반드시 자동화된 테스트로 커버한다. SecurityFilterChain 변경, 인증 로직 변경 시 회귀 방지 목적.

| 시나리오 | 검증 내용 | 예상 결과 |
|---|---|---|
| 테넌트 불일치 | JWT `tenant_id`와 요청 서브도메인이 다를 때 | `403 Forbidden` + `SECURITY.TENANT_MISMATCH` 감사 이벤트 |
| PKCE 누락 | `authorization_code` 요청 시 `code_challenge` 미포함 | 요청 거부 |
| Admin 토큰 교차 사용 | 일반 사용자(`ROLE_USER`) 토큰으로 `/admin/**` 접근 | `403 Forbidden` |
| JWKS 캐시 갱신 | 키 로테이션 후 5분 이내 신규 키로 서명된 토큰 | 검증 성공 |
| SLO SSRF 차단 | `logoutUri`에 사설 IP(`127.0.0.1`, `10.x`) 등록 시도 | 등록 거부 |
| Rate Limit 초과 | 제한 초과 요청 전송 | `429 Too Many Requests` + `Retry-After` 헤더 |
| 감사 로그 checksum | 저장된 감사 이벤트의 HMAC-SHA256 checksum | 검증 통과 |
| MFA 백업코드 재사용 | 이미 사용된 백업코드로 인증 시도 | 인증 실패 |
| 페더레이션 nonce 리플레이 | 동일 `nonce`로 ID Token 재제출 | 인증 거부 |

---

## 10. 보안 정책 명세

이 섹션은 보안 관련 규칙을 에이전트가 단일 위치에서 참조할 수 있도록 통합한 것이다.
각 모듈 구현 시 이 섹션의 규칙을 반드시 준수한다.

### 10.1 CORS 정책

| 경로 패턴 | 허용 Origin | Credentials |
|---|---|---|
| `/oauth2/**`, `/userinfo` | 등록된 클라이언트의 `redirectUri` origin만 허용 | `true` |
| `/admin/**` | 관리자 포털 origin만 허용 (환경 변수 `SSO_ADMIN_ALLOWED_ORIGINS`) | `true` |
| `/api/**` | 등록된 Resource Server origin 허용 | `true` |
| `/.well-known/**` | 모든 origin (`*`) | `false` |

- CORS 설정은 각 `SecurityFilterChain` 내부에서 `http.cors()` + `CorsConfigurationSource` Bean으로 적용한다.
- `Access-Control-Allow-Methods`: 필요한 HTTP 메서드만 명시적으로 허용.

### 10.2 CSRF 정책

| FilterChain | CSRF 보호 | 사유 |
|---|---|---|
| Authorization Server (Order=1) | **활성화** | 쿠키 기반 로그인/동의 화면 사용 |
| Admin (Order=2) | **비활성화** | JWT Bearer 전용 (쿠키 미사용) |
| Resource Server (Order=3) | **비활성화** | JWT Bearer 전용 (쿠키 미사용) |

- Authorization Server FilterChain은 Spring Authorization Server 기본 CSRF 보호를 유지한다.
- CSRF 비활성화 시 반드시 `http.csrf(csrf -> csrf.disable())` 명시.

### 10.3 보안 헤더

모든 HTTP 응답에 아래 보안 헤더를 포함한다. `SecurityHeadersConfig` 클래스에서 일괄 설정.

```
Strict-Transport-Security: max-age=31536000; includeSubDomains; preload
Content-Security-Policy: default-src 'self'; frame-ancestors 'none'
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
Referrer-Policy: strict-origin-when-cross-origin
```

- 인증 관련 응답(`/oauth2/token`, `/oauth2/authorize` 등)에는 `Cache-Control: no-store` 추가.
- CSP `frame-ancestors 'none'`은 iframe 삽입 공격(Clickjacking) 방지 목적.

### 10.4 Rate Limiting

**구현 위치:** `RateLimitingFilter` (`sso-bootstrap` 모듈), Redis Sliding Window 알고리즘.

| 대상 | 제한 | 윈도우 | 식별자 |
|---|---|---|---|
| 로그인 시도 (계정당) | 5회 실패 | 10분 슬라이딩 | `tenantId:email` |
| 로그인 시도 (IP당) | 20회 실패 | 10분 슬라이딩 | `tenantId:ip` |
| Token 요청 (클라이언트당) | 100 req/s | 1초 | `clientId` |
| `/oauth2/authorize` | 30 req/min | 1분 | `tenantId:userId` |
| 감사 로그 다운로드 | 5 req/min | 1분 | `adminUserId` |

**Redis 키 패턴:** `sso:ratelimit:{tenant}:{endpoint}:{identifier}`

**초과 시 응답:**
- HTTP `429 Too Many Requests`
- `Retry-After` 헤더 포함 (남은 차단 시간, 초 단위)
- `SECURITY.RATE_LIMIT_EXCEEDED` 감사 이벤트 발행

### 10.5 클라이언트 인증 강도

- `client_credentials` 그랜트: 클라이언트 시크릿 최소 256비트(32바이트). `RegisterClientUseCase`에서 생성 시 강제.
- 시크릿은 Vault Transit Engine으로 암호화 저장 (섹션 2.4 참조).
- 시크릿 로테이션 주기: 최대 90일 권장. 관리자가 강제 로테이션 가능 (`RotateClientSecretUseCase`).
- 시크릿 로테이션 시 이전 시크릿은 grace period(24시간) 동안 유효 → 이후 자동 폐기.

### 10.6 Redis 보안

| 항목 | 요구사항 |
|---|---|
| 인증 | Redis AUTH 필수. 비밀번호는 Vault KV에서 주입 (`secret/sso/redis-password`). |
| 전송 암호화 | 운영 환경: Redis TLS 활성화 필수 (`spring.data.redis.ssl.enabled=true`). |
| 접근 제어 | Redis ACL: SSO 전용 사용자 생성. `sso:*` 키 패턴만 읽기/쓰기 허용. |
| 키 만료 | 모든 Redis 키에 TTL 설정 필수. 무기한 키 생성 금지 (OutboxPoller 제외). |

### 10.7 Actuator 접근 제어

- Actuator 포트 `8081`은 내부망 CIDR에서만 접근 허용한다.
- 허용 CIDR: `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`, `127.0.0.1/32`
- 구현: `WebSecurityCustomizer` 또는 별도 `SecurityFilterChain`에서 IP 기반 접근 제어.

| 엔드포인트 | 접근 정책 |
|---|---|
| `/actuator/health` | 공개 (readiness/liveness probe) |
| `/actuator/prometheus` | 내부망 IP만 허용 (Prometheus 스크래핑) |
| `/actuator/loggers` | 내부망 IP + Basic Auth |
| 그 외 `/actuator/**` | 내부망 IP만 허용 |

### 10.8 SQL Injection 방지

Query 레이어(Native SQL, JOOQ)에서 반드시 준수해야 하는 규칙:

- Native Query 작성 시 **파라미터 바인딩 필수** (`:paramName` 또는 `?` 사용).
- 문자열 연결(`"SELECT ... WHERE id = '" + variable + "'"`)로 쿼리 생성 **절대 금지**.
- JOOQ DSL 사용 시 `DSL.val()` 또는 `DSL.param()`으로 값 바인딩.
- 동적 정렬/필터링이 필요한 경우: 허용된 컬럼명 화이트리스트 기반으로만 조합 (사용자 입력 직접 삽입 금지).
