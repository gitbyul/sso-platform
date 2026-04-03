# SSO Platform

엔터프라이즈 멀티테넌트 SSO 플랫폼 (Spring Boot, PostgreSQL, Redis, Vault).

| 문서 | 용도 |
|------|------|
| [`docs/DEVELOPMENT_PLAN.md`](docs/DEVELOPMENT_PLAN.md) | 로드맵, ADR, Phase |
| [`docs/AGENT_SPEC.md`](docs/AGENT_SPEC.md) | 구현 강제 규격 (에이전트·코드 기준) |
| [`docs/DATABASE_RULES.md`](docs/DATABASE_RULES.md) | PostgreSQL 명명(§0·§2)·인덱스·제약조건·모니터링 |

**우선순위:** 구현·보안·코드 구조는 `AGENT_SPEC.md`가 우선. DB 객체 명명·인덱스 설계는 `DATABASE_RULES.md` §0·§2가 우선. 로드맵과 상충 시 `DEVELOPMENT_PLAN.md`를 `AGENT_SPEC.md`에 맞게 갱신한다.

---

## 사전 요구 사항

- [Docker](https://docs.docker.com/get-docker/) (Compose V2 포함)
- 애플리케이션 이미지 빌드 시 Docker가 **JDK 25 멀티 스테이지 빌드**를 수행하므로, 로컬에 별도 JDK가 없어도 된다.

---

## Docker Compose로 실행하기

저장소 루트(`sso-platform/`)에서 실행한다.

### 기본 (백그라운드)

```bash
docker compose up -d --build
```

- 첫 실행 시 `sso-bootstrap` 이미지가 Gradle로 빌드되므로 시간이 걸릴 수 있다.
- `postgres`, `redis`, `vault`가 헬스체크에 통과한 뒤 `sso-bootstrap` 컨테이너가 기동한다.

### 로그 확인

```bash
docker compose logs -f sso-bootstrap
```

전체 서비스:

```bash
docker compose logs -f
```

### 중지

```bash
docker compose down
```

데이터 볼륨까지 삭제하려면:

```bash
docker compose down -v
```

> `postgres_data`, `redis_data` 볼륨을 지우면 DB·Redis 데이터가 초기화된다.

---

## 구성 서비스

| 서비스 | 컨테이너명 | 호스트 포트 | 설명 |
|--------|------------|-------------|------|
| **postgres** | `sso-postgres` | **`5433` → 컨테이너 `5432`** | DB `sso_platform`, 사용자 `sso` / 비밀번호 `sso`. 호스트·SQLTools·로컬 앱은 **`127.0.0.1:5433`** |
| **redis** | `sso-redis` | `6379` | AOF 활성화 |
| **vault** | `sso-vault` | `8200` | **개발 모드** (`-dev`). 루트 토큰 `root` (운영 금지) |
| **sso-bootstrap** | `sso-bootstrap` | `8080` | Spring Boot 앱 (`SPRING_PROFILES_ACTIVE=docker`) |

DB 스키마·테이블은 `sso-bootstrap` 기동 시 Flyway(`classpath:db/migration`)가 적용한다. 도메인 네임스페이스에는 `shared`, `tenant`, `identity`, `client`, `audit` 등이 포함된다.  
애플리케이션 기동 시 **Flyway**가 [`sso-bootstrap/src/main/resources/db/migration/`](sso-bootstrap/src/main/resources/db/migration/)의 스크립트를 적용한다(`shared`·`tenant` 테이블 등). `shared` 스키마는 Flyway에서만 생성된다.

---

## 애플리케이션 환경 변수 (Compose)

`sso-bootstrap` 서비스에 설정된 값 요약:

| 변수 | 값 |
|------|-----|
| `SPRING_PROFILES_ACTIVE` | `docker` |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://postgres:5432/sso_platform` |
| `SPRING_DATASOURCE_USERNAME` / `PASSWORD` | `sso` / `sso` |
| `SPRING_DATA_REDIS_HOST` / `PORT` | `redis` / `6379` |
| `SSO_VAULT_ADDRESS` | `http://vault:8200` |
| `SSO_VAULT_TOKEN` | `root` |

호스트에서 앱을 띄우고 인프라만 Docker로 쓰는 경우 **`local` 프로필**을 쓴다. [`sso-bootstrap/src/main/resources/application-local.properties`](sso-bootstrap/src/main/resources/application-local.properties)에 DB(**게시 포트 `5433`**), Redis, Vault URL이 정의되어 있다.

---

## 인프라만 실행 (앱은 로컬 Gradle)

앱은 IDE나 `./gradlew :sso-bootstrap:bootRun`으로 돌리고, DB·Redis·Vault만 컨테이너로 쓰려면 `docker-compose.yml`에서 `sso-bootstrap` 서비스를 임시로 주석 처리하거나, 아래처럼 특정 서비스만 올린다.

```bash
docker compose up -d postgres redis vault
```

이후 로컬 앱의 DB URL은 `jdbc:postgresql://127.0.0.1:5433/sso_platform` (`application-local.properties`와 동일)으로 맞춘다.

---

## 코드 수정 시 자동 반영 (개발)

현재 `docker compose up`으로 올리는 **`sso-bootstrap` 컨테이너는 JAR를 이미지에 넣어 실행**하므로, 소스만 고치면 컨테이너 안 코드가 바뀌지 않는다. 매번 반영하려면 `docker compose up -d --build sso-bootstrap`처럼 **이미지를 다시 빌드**해야 한다.

**일상 개발에서는 아래 조합을 권장한다.**

1. **인프라만 Docker**  
   `docker compose up -d postgres redis vault`

2. **앱은 호스트에서 Gradle 실행** (JDK 25 필요)  
   ```bash
   ./gradlew :sso-bootstrap:bootRun
   ```  
   `:sso-bootstrap:bootRun`은 기본으로 `spring.profiles.active=local`을 넣어 `application-local.properties`를 로드한다. IDE에서 메인 클래스만 실행할 때는 VM 옵션 `--spring.profiles.active=local` 또는 환경 변수 `SPRING_PROFILES_ACTIVE=local`을 지정한다. (통합 테스트용 예시는 [`sso-bootstrap/src/test/resources/application-test.properties`](sso-bootstrap/src/test/resources/application-test.properties) 참고)

3. **Spring Boot DevTools**  
   `sso-bootstrap`에 `spring-boot-devtools`를 `developmentOnly`로 넣어 두었다. 클래스패스가 바뀌면 애플리케이션이 **자동 재시작**된다.

**IDE에서 자동 재시작이 잘 되게 하려면**

- **IntelliJ IDEA:**  
  - *Settings → Build, Execution, Deployment → Compiler* 에서 **Build project automatically** 활성화  
  - *Advanced Settings* 또는 Registry에서 **compiler.automake.allow.when.app.running** 허용  
  - 파일 저장 시 빌드가 돌아가면 DevTools가 재시작을 트리거한다.

- **VS Code / 다른 에디터:** 저장 후 한 번 `./gradlew :sso-bootstrap:compileJava`를 실행하거나, IDE가 Gradle 빌드를 자동으로 돌리도록 맞춘다.

**`resources`만 바꿀 때** (예: `application.properties`) 대부분 재시작 없이 일부가 리로드되지만, 설정 구조에 따라 재시작이 필요할 수 있다.

**정리:** Docker로 앱까지 묶어 쓰는 방식은 **통합 검증·배포 연습**에 적합하고, **코드를 자주 바꾸는 개발**은 **Compose는 DB·Redis·Vault만 + 로컬 `bootRun` + DevTools**가 맞다.

---

## 문제 해결

- **포트 충돌:** 호스트 PostgreSQL 기본 포트(`5432`)와 겹치지 않도록 **게시 포트는 `5433`**이다. `6379`, `8200`, `8080` 등이 겹치면 `docker-compose.yml`의 `ports`를 조정하고, DB 게시 포트를 바꿨다면 `application-local.properties`, SQLTools `.vscode/settings.json`, `application-test.properties`의 포트도 같이 맞춘다.
- **PostgreSQL `28P01` / 사용자 `sso` 비밀번호 인증 실패 (SQLTools 포함):** 앱·클라이언트가 **Docker의 `sso-postgres`가 아닌 다른 PostgreSQL**(로컬 설치 등)에 붙었을 때 자주 난다. 로컬에서는 **`127.0.0.1:5433`**으로 붙어야 한다. `docker compose ps`로 `sso-postgres`가 떠 있는지 본다. 컨테이너가 맞다면 아래로 접속이 되어야 한다.  
  `docker exec -it sso-postgres psql -U sso -d sso_platform -c 'select 1'`  
  예전에 다른 계정으로 초기화된 **`postgres_data` 볼륨**이면 `POSTGRES_USER`/`POSTGRES_PASSWORD`가 다시 적용되지 않는다. 개발용으로 DB를 비워도 된다면 `docker compose down -v` 후 `docker compose up -d postgres redis vault`로 볼륨을 새로 만든다. 포트만 바꿨다면 `application-local.properties`의 JDBC URL 포트도 같이 맞춘다.  
  OS·터미널에 **`SPRING_DATASOURCE_*` 환경 변수**가 남아 있으면 `application-local.properties`보다 우선한다. 잘못된 값이면 제거하거나 올바른 값으로 맞춘다.
- **빌드 실패:** `docker compose build sso-bootstrap --no-cache`로 캐시 없이 재시도한다.
- **Vault:** 현재 구성은 **개발 전용**이다. 운영에서는 `-dev` 모드와 고정 루트 토큰을 사용하지 않는다.
