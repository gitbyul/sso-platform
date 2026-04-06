package com.gitbyul.shared.i18n.jpa;

import com.gitbyul.shared.i18n.TenantDefaultLocaleLookup;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import org.springframework.util.StringUtils;

public class JpaTenantDefaultLocaleLookup implements TenantDefaultLocaleLookup {

    private final EntityManager entityManager;

    public JpaTenantDefaultLocaleLookup(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Optional<String> findDefaultLocaleByTenantId(String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            return Optional.empty();
        }
        @SuppressWarnings("unchecked")
        List<String> rows =
                entityManager
                        .createNativeQuery(
                                "SELECT default_locale FROM tenant.tenant_settings WHERE tenant_id = ?1")
                        .setParameter(1, tenantId)
                        .setMaxResults(1)
                        .getResultList();
        if (rows.isEmpty() || rows.getFirst() == null) {
            return Optional.empty();
        }
        return Optional.of(rows.getFirst().trim());
    }
}
