-- tenant: Tenant aggregate (AGENT_SPEC.md §2.2)
CREATE SCHEMA IF NOT EXISTS tenant;

CREATE TABLE IF NOT EXISTS tenant.tenants (
    tenant_id  VARCHAR(100) PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    domain     VARCHAR(255) NOT NULL,
    status     VARCHAR(32)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_tenants_domain UNIQUE (domain)
);
