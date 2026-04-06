-- V0.x: 플랫폼 공통 DB 기반 i18n (agent-i18n-db-messages.mdc)
CREATE TABLE IF NOT EXISTS shared.localized_messages (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    message_key  VARCHAR(255) NOT NULL,
    locale       VARCHAR(16)  NOT NULL,
    message_text TEXT         NOT NULL,
    tenant_id    VARCHAR(100),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_localized_messages_platform
    ON shared.localized_messages (message_key, locale)
    WHERE tenant_id IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_localized_messages_tenant
    ON shared.localized_messages (tenant_id, message_key, locale)
    WHERE tenant_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_localized_messages_lookup_platform
    ON shared.localized_messages (message_key, locale)
    WHERE tenant_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_localized_messages_lookup_tenant
    ON shared.localized_messages (tenant_id, message_key, locale)
    WHERE tenant_id IS NOT NULL;
