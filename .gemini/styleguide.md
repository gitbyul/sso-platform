# SSO Platform — Gemini Code Assist 코드 리뷰 스타일 가이드

## 언어 (필수)

- **PR 요약, 인라인 리뷰 코멘트, 도움말 메시지 등 Gemini가 게시하는 모든 텍스트는 한국어로 작성한다.**
- 코드 식별자(클래스·메서드·파일 경로·설정 키 등)는 원문(영문)을 유지한다.
- UI/로그/에러 메시지가 제품 정책상 영어만 허용되는 경우, 그 제약이 PR에 명시되어 있으면 예외로 둔다.

## 프로젝트 맥락

- **그룹·패키지:** `com.gitbyul`
- **런타임·프레임워크:** Java 25, Spring Boot 4.0.x, Gradle Kotlin DSL 멀티모듈
- **실행 모듈:** `sso-bootstrap`만 `@SpringBootApplication`을 둔다. 나머지는 `java-library` 바운디드 컨텍스트 모듈이다.
- **인프라 스택:** PostgreSQL 16, Redis 7, Vault(실연동은 통합 테스트에서 격리)
- **도메인 문서:** 제품/기술 계약은 저장소의 `AGENTS.md`, `.cursor/docs/PRD.md`, `TRD.md`를 따른다. 리뷰 시 보안·멀티테넌시·감사 요구와 충돌이 있으면 이들을 우선한다.

## 아키텍처·경계

- **컨텍스트 간:** `sso-shared-kernel` 외 상호 직접 의존을 지적한다.
- **CQRS:** 커맨드 처리에서 조회 로직 혼입, 쿼리 핸들러에서 상태 변경이 있으면 높은 심각도로 본다. 쿼리 측은 `readOnly` 트랜잭션 관례를 확인한다.
- **패키지 관례:** 컨텍스트 모듈은 `command` / `query` / `config` 구조와 `application`(usecase/handler), `domain`, `infrastructure` 분리를 기대한다. Query 저장소는 JPA Entity 노출·조회 전용 규칙(JOOQ/Native SQL 등)과의 정합성을 본다.
- **네이밍:** Aggregate, Domain Event, UseCase, CommandHandler, Query, Repository 포트/어댑터, JPA 접미사 등 팀 네이밍 규칙과 불일치하면 일관성 이슈로 코멘트한다.

## Java·Spring 구현 습관

- **모델링:** DTO·값 객체·명령/조회 결과에 `record` 사용 여부, 허용된 하위 타입에 `sealed`와 패턴 매칭으로 전체성을 확보했는지 검토한다.
- **시간·텍스트:** `java.time` 사용, 멀티라인은 텍스트 블록 관례를 확인한다.
- **설정:** 보안·테넌시를 약화시키는 기본값이나 민감 설정의 하드코딩을 지적한다. `@ConfigurationProperties`(불변/record)와 외부화가 적절한지 본다.
- **동시성:** 블로킹 I/O와 가상 스레드 전환 시 핀닝 가능성이 있으면 리스크로 언급한다(근거 있는 경우에 한함).

## 보안·컴플라이언스 (높은 우선순위)

다음은 가능한 한 **구체적 재현 조건**과 **완화 방안**을 함께 제시한다.

- JWT 클레임의 `tenant_id`(또는 동등한 테넌트 식별자)와 요청 컨텍스트 테넌트 불일치, IDOR, 권한 상승 가능성
- OAuth2/OIDC: PKCE, state/nonce 리플레이, 리다이렉트 URI 검증 누락
- 관리자 전용 경로(`/admin/**` 등 정책상 관리자만)에 일반 주체 접근 가능 여부
- 로그·감사 이벤트에 비밀번호·토큰·키·개인정보 평문 기록
- Rate limit, SLO/콜백 URL 검증(사설 IP 등), CSRF 보호 필요 여부(세션/쿠키 기반 흐름)
- 의존성·설정이 OWASP 관점에서 취약한 패턴(예: 안전하지 않은 랜덤, 취약한 MAC/암호 스위트)인 경우

## 테스트

- **신규 동작·버그 수정**에 테스트 부재가 있으면 블로킹 이슈로 취급한다.
- **통합 테스트:** DB는 H2 대신 **PostgreSQL(Testcontainers)**, Redis 의존 시 **Redis(Testcontainers)** 관례와 맞는지 본다.
- **보안 회귀:** PR이 인증·토큰·테넌트 바인딩·관리자 경로·감사·레이트 리밋·로그아웃 URL 등에 손대면 관련 회귀 시나리오(또는 동등 검증)가 있는지 확인한다.
- **Fake/Mock:** 프로덕션에 `Stub`/`Dummy`/무의미한 기본 반환/`UnsupportedOperationException("stub")` 등 임시 구현이 있으면 반드시 지적한다. Mock 위주 테스트는 `verify`/의미 있는 assert가 있는지 본다.

## 리뷰 톤

- **비난이 아닌 개선 제안:** 문제, 영향, 권장 수정 방향을 짧게 정리한다.
- **추측과 사실 구분:** 동작을 확정할 수 없으면 가정을 명시하고, 확인용 체크리스트를 제안한다.
- **너무 사소한 포맷**만 다루는 코멘트는 남기지 않는다(팀에 포맷터/린터가 있으면 그에 맡긴다).

## 도구·빌드

- Gradle Kotlin DSL(`*.gradle.kts`), 버전 카탈로그/공통 플러그인 관례를 존중한다.
- Flyway 마이그레이션·DDL 네이밍은 저장소의 데이터베이스 규칙 문서와 충돌 시 지적한다.

---

이 가이드는 `gitbyul/sso-platform` 저장소 전용이다. 상위 그룹 스타일 가이드가 있으면 본 문서와 **합성**되어 적용될 수 있다.
