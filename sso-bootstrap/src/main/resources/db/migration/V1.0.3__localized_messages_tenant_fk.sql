-- tenant 테이블 생성(V1.0.0) 이후 localized_messages.tenant_id FK 부여
ALTER TABLE shared.localized_messages
    ADD CONSTRAINT fk_localized_messages_tenants_tenant_id
        FOREIGN KEY (tenant_id) REFERENCES tenant.tenants (tenant_id) ON DELETE CASCADE;
