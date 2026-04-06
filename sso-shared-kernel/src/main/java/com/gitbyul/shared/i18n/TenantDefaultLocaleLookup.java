package com.gitbyul.shared.i18n;

import java.util.Optional;

/**
 * {@code tenant.tenant_settings.default_locale} 조회.
 */
public interface TenantDefaultLocaleLookup {

    Optional<String> findDefaultLocaleByTenantId(String tenantId);
}
