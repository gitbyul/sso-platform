-- tenant: TenantSettings / PasswordPolicy JSON (AGENT_SPEC.md §2.2)
CREATE TABLE IF NOT EXISTS tenant.tenant_settings (
    tenant_id               VARCHAR(100) PRIMARY KEY
        REFERENCES tenant.tenants (tenant_id) ON DELETE CASCADE,
    password_policy         JSONB,
    mfa_required            BOOLEAN     NOT NULL DEFAULT false,
    session_timeout_minutes INTEGER     NOT NULL DEFAULT 60,
    max_concurrent_sessions INTEGER     NOT NULL DEFAULT 5,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);
