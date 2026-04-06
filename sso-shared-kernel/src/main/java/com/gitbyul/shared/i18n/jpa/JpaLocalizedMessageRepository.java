package com.gitbyul.shared.i18n.jpa;

import com.gitbyul.shared.i18n.LocalizedMessageRepository;
import java.util.Locale;
import java.util.Optional;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

/**
 * 테넌트 행 우선, 없으면 플랫폼({@code tenant_id IS NULL}) 행. 로케일은 태그 후 언어만 fallback.
 */
public class JpaLocalizedMessageRepository implements LocalizedMessageRepository {

    private final LocalizedMessageJpaRepository jpa;

    public JpaLocalizedMessageRepository(LocalizedMessageJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<String> findMessage(String messageKey, Locale locale, @Nullable String tenantId) {
        if (!StringUtils.hasText(messageKey) || locale == null) {
            return Optional.empty();
        }
        String fullTag = locale.toLanguageTag();
        String languageOnly = locale.getLanguage();
        Optional<String> exact = tryResolve(messageKey, fullTag, tenantId);
        if (exact.isPresent()) {
            return exact;
        }
        if (StringUtils.hasText(languageOnly) && !languageOnly.equalsIgnoreCase(fullTag)) {
            return tryResolve(messageKey, languageOnly, tenantId);
        }
        return Optional.empty();
    }

    private Optional<String> tryResolve(String messageKey, String localeTag, @Nullable String tenantId) {
        if (StringUtils.hasText(tenantId)) {
            Optional<String> tenantRow =
                    jpa.findByTenantIdAndMessageKeyAndLocale(tenantId, messageKey, localeTag)
                            .map(LocalizedMessageJpaEntity::getMessageText);
            if (tenantRow.isPresent()) {
                return tenantRow;
            }
        }
        return jpa.findByMessageKeyAndLocaleAndTenantIdIsNull(messageKey, localeTag)
                .map(LocalizedMessageJpaEntity::getMessageText);
    }
}
