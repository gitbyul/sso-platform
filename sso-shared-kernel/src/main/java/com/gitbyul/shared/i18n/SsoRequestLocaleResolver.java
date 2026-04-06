package com.gitbyul.shared.i18n;

import com.gitbyul.shared.tenant.TenantContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

/**
 * (1) 테넌트 {@code default_locale} → (2) {@code Accept-Language} → (3) 기본 로케일.
 * 사용자 프로필 로케일은 후속 단계에서 앞단에 삽입한다.
 */
public class SsoRequestLocaleResolver {

    private final TenantDefaultLocaleLookup tenantDefaultLocaleLookup;
    private final AcceptHeaderLocaleResolver acceptHeaderLocaleResolver;

    public SsoRequestLocaleResolver(
            TenantDefaultLocaleLookup tenantDefaultLocaleLookup,
            AcceptHeaderLocaleResolver acceptHeaderLocaleResolver) {
        this.tenantDefaultLocaleLookup = tenantDefaultLocaleLookup;
        this.acceptHeaderLocaleResolver = acceptHeaderLocaleResolver;
    }

    public Locale resolveLocale(HttpServletRequest request) {
        String tenantId = TenantContextHolder.get();
        if (StringUtils.hasText(tenantId)) {
            var tag = tenantDefaultLocaleLookup.findDefaultLocaleByTenantId(tenantId);
            if (tag.isPresent() && StringUtils.hasText(tag.get())) {
                return Locale.forLanguageTag(tag.get().trim().replace('_', '-'));
            }
        }
        return acceptHeaderLocaleResolver.resolveLocale(request);
    }
}
