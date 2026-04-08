# SSO Platform Agent Guide

이 문서는 에이전트 작업의 루트 진입점이다. 상세 구현 규칙은 `.cursor/rules/*.mdc`에 분리한다.

## 우선순위

1. 보안/구현 규칙은 이 문서와 `.cursor/rules/*.mdc`를 따른다.
2. DDL 객체 명명, 인덱스, 제약조건, 운영 점검은 `.cursor/docs/DATABASE_RULES.md`를 따른다.
3. 로드맵/일정 정보는 `.cursor/docs/DEVELOPMENT_PLAN.md`를 참고한다.
4. 제품 요구사항은 `.cursor/docs/PRD.md`, 기술 요구사항(구현·검증 계약)은 `.cursor/docs/TRD.md`를 참고한다. (구현 규칙과 충돌 시 1번이 우선.)

## 기본 컨텍스트

- Java 25, Spring Boot 4.0.5, Gradle Kotlin DSL 멀티모듈
- 실행 진입점은 `sso-bootstrap` 단일 모듈
- 컨텍스트 모듈은 `sso-shared-kernel`만 의존하고 상호 직접 의존하지 않는다

## 규칙 인덱스

### 공통 규칙

| 구분                      | 규칙 파일                                         |
| ------------------------- | ------------------------------------------------- |
| Git·GitHub (커밋·이슈·PR) | `.cursor/rules/agent-scm-github.mdc`              |
| 프로젝트 컨텍스트         | `.cursor/rules/agent-project-context.mdc`         |
| Java 25 / Spring Boot 4   | `.cursor/rules/agent-java25-springboot4-guidelines.mdc` |
| 패키지/경계/네이밍        | `.cursor/rules/agent-architecture-boundaries.mdc` |
| CQRS 패턴                 | `.cursor/rules/agent-cqrs-patterns.mdc`           |
| 이벤트/Outbox             | `.cursor/rules/agent-events-outbox.mdc`           |
| 테넌트 식별/바인딩        | `.cursor/rules/agent-tenancy-and-filters.mdc`     |
| Authorization Server 연동 | `.cursor/rules/agent-authorization-server.mdc`    |
| Redis Streams             | `.cursor/rules/agent-redis-streams.mdc`           |
| Flyway 버전 관리          | `.cursor/rules/agent-flyway-versioning.mdc`       |
| 모니터링/로그             | `.cursor/rules/agent-observability.mdc`           |
| 감사 모델/민감정보/체크섬 | `.cursor/rules/agent-audit-spec.mdc`              |
| 보안 정책                 | `.cursor/rules/agent-security-platform.mdc`       |
| 테스트 통합 규칙          | `.cursor/rules/tdd-strict.mdc`                    |

### 모듈 규칙

| 모듈                      | 규칙 파일                                              |
| ------------------------- | ------------------------------------------------------ |
| sso-shared-kernel         | `.cursor/rules/agent-module-shared-kernel.mdc`         |
| sso-tenant-context        | `.cursor/rules/agent-module-tenant-context.mdc`        |
| sso-identity-context      | `.cursor/rules/agent-module-identity-context.mdc`      |
| sso-client-context        | `.cursor/rules/agent-module-client-context.mdc`        |
| sso-authorization-context | `.cursor/rules/agent-module-authorization-context.mdc` |
| sso-key-context           | `.cursor/rules/agent-module-key-context.mdc`           |
| sso-session-context       | `.cursor/rules/agent-module-session-context.mdc`       |
| sso-audit-context         | `.cursor/rules/agent-module-audit-context.mdc`         |
| sso-federation-context    | `.cursor/rules/agent-module-federation-context.mdc`    |
| sso-admin-context         | `.cursor/rules/agent-module-admin-context.mdc`         |
| sso-bootstrap             | `.cursor/rules/agent-module-bootstrap.mdc`             |

## 섹션 매핑 (폐기 예정 문서 기준)

| 이전 `AGENT_SPEC` 섹션 | 신규 위치                           |
| ---------------------- | ----------------------------------- |
| §0                     | `agent-project-context.mdc`         |
| §1                     | `agent-architecture-boundaries.mdc` |
| §2.1~§2.11             | `agent-module-*.mdc`                |
| §3.1                   | `agent-cqrs-patterns.mdc`           |
| §3.2~§3.3              | `agent-events-outbox.mdc`           |
| §3.4                   | `agent-tenancy-and-filters.mdc`     |
| §3.5                   | `agent-security-platform.mdc`       |
| §4.1~§4.4              | `agent-audit-spec.mdc`              |
| §5.1~§5.4              | `agent-authorization-server.mdc`    |
| §6.1~§6.3              | `agent-redis-streams.mdc`           |
| §7.1~§7.3              | `agent-flyway-versioning.mdc`       |
| §8.1~§8.4              | `agent-observability.mdc`           |
| §9.1~§9.6              | `tdd-strict.mdc` (단일 통합)        |
| §10.1~§10.8            | `agent-security-platform.mdc`       |
