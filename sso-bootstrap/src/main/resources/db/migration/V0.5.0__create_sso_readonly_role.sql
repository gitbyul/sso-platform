-- V0.x: 리포팅·로컬 클라이언트용 읽기 전용 역할 (런타임 애플리케이션은 datasource 의 sso 계정 사용)
-- 비개발 환경에서는 비밀번호 회전·Vault 등으로 관리할 것.
-- 역할이 이미 있으면 생성만 건너뜀(수동 생성·재실행 DB 멱등). GRANT/COMMENT 는 매 실행 적용.
DO
$$
    BEGIN
        IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sso_readonly') THEN
            CREATE ROLE sso_readonly WITH
                LOGIN
                NOSUPERUSER
                INHERIT
                NOCREATEDB
                NOCREATEROLE
                NOREPLICATION
                CONNECTION LIMIT -1
                PASSWORD 'sso_readonly';
        END IF;
    END
$$;

COMMENT ON ROLE sso_readonly IS 'Read-only SELECT on application schemas; not for application runtime datasource.';

GRANT CONNECT ON DATABASE sso_platform TO sso_readonly;

GRANT USAGE ON SCHEMA public TO sso_readonly;
GRANT USAGE ON SCHEMA shared TO sso_readonly;
GRANT USAGE ON SCHEMA tenant TO sso_readonly;
GRANT USAGE ON SCHEMA identity TO sso_readonly;
GRANT USAGE ON SCHEMA client TO sso_readonly;
GRANT USAGE ON SCHEMA audit TO sso_readonly;

GRANT SELECT ON ALL TABLES IN SCHEMA public TO sso_readonly;
GRANT SELECT ON ALL TABLES IN SCHEMA shared TO sso_readonly;
GRANT SELECT ON ALL TABLES IN SCHEMA tenant TO sso_readonly;
GRANT SELECT ON ALL TABLES IN SCHEMA identity TO sso_readonly;
GRANT SELECT ON ALL TABLES IN SCHEMA client TO sso_readonly;
GRANT SELECT ON ALL TABLES IN SCHEMA audit TO sso_readonly;

GRANT SELECT ON ALL SEQUENCES IN SCHEMA shared TO sso_readonly;
GRANT SELECT ON ALL SEQUENCES IN SCHEMA tenant TO sso_readonly;
GRANT SELECT ON ALL SEQUENCES IN SCHEMA identity TO sso_readonly;
GRANT SELECT ON ALL SEQUENCES IN SCHEMA client TO sso_readonly;
GRANT SELECT ON ALL SEQUENCES IN SCHEMA audit TO sso_readonly;

ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO sso_readonly;
ALTER DEFAULT PRIVILEGES IN SCHEMA shared GRANT SELECT ON TABLES TO sso_readonly;
ALTER DEFAULT PRIVILEGES IN SCHEMA tenant GRANT SELECT ON TABLES TO sso_readonly;
ALTER DEFAULT PRIVILEGES IN SCHEMA identity GRANT SELECT ON TABLES TO sso_readonly;
ALTER DEFAULT PRIVILEGES IN SCHEMA client GRANT SELECT ON TABLES TO sso_readonly;
ALTER DEFAULT PRIVILEGES IN SCHEMA audit GRANT SELECT ON TABLES TO sso_readonly;

ALTER DEFAULT PRIVILEGES IN SCHEMA shared GRANT SELECT ON SEQUENCES TO sso_readonly;
ALTER DEFAULT PRIVILEGES IN SCHEMA tenant GRANT SELECT ON SEQUENCES TO sso_readonly;
ALTER DEFAULT PRIVILEGES IN SCHEMA identity GRANT SELECT ON SEQUENCES TO sso_readonly;
ALTER DEFAULT PRIVILEGES IN SCHEMA client GRANT SELECT ON SEQUENCES TO sso_readonly;
ALTER DEFAULT PRIVILEGES IN SCHEMA audit GRANT SELECT ON SEQUENCES TO sso_readonly;
