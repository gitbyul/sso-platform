-- V1.0.x: 테넌트 기본 UI 로케일 (i18n LocaleResolver)
ALTER TABLE tenant.tenant_settings
    ADD COLUMN IF NOT EXISTS default_locale VARCHAR(10);
