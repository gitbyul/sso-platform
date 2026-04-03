-- 플랫폼 공통: 기본 public 스키마에 PUBLIC 역할의 CREATE 제거 (테이블 등은 전용 스키마에만).
-- 버전 V1.0.2: 이미 V1.0.x가 배포된 환경에서 Flyway 역전(out-of-order)을 피하기 위해 tenant 대역 다음 번호 사용.
REVOKE CREATE ON SCHEMA public
FROM PUBLIC;
COMMENT ON SCHEMA public IS 'Application tables live in shared, tenant, identity, etc. CREATE on public revoked from PUBLIC.';