-- V0.x: 플랫폼 공통 — public 에 PUBLIC 역할 CREATE 제거 (앱 객체는 전용 스키마에만)
REVOKE CREATE ON SCHEMA public
FROM PUBLIC;
COMMENT ON SCHEMA public IS 'Application tables live in shared, tenant, identity, etc. CREATE on public revoked from PUBLIC.';
