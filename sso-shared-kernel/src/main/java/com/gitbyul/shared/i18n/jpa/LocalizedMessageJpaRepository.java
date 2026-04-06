package com.gitbyul.shared.i18n.jpa;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocalizedMessageJpaRepository extends JpaRepository<LocalizedMessageJpaEntity, UUID> {

    Optional<LocalizedMessageJpaEntity> findByTenantIdAndMessageKeyAndLocale(
            String tenantId, String messageKey, String locale);

    Optional<LocalizedMessageJpaEntity> findByMessageKeyAndLocaleAndTenantIdIsNull(String messageKey, String locale);
}
