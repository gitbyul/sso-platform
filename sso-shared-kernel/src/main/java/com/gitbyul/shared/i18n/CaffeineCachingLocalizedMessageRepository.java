package com.gitbyul.shared.i18n;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import org.springframework.lang.Nullable;

/**
 * {@link LocalizedMessageRepository} 결과 캐시. 운영에서 번역 갱신 시 TTL 만료 또는 재기동으로 반영한다.
 */
public class CaffeineCachingLocalizedMessageRepository implements LocalizedMessageRepository {

    private final LocalizedMessageRepository delegate;
    private final Cache<String, Optional<String>> cache;

    public CaffeineCachingLocalizedMessageRepository(LocalizedMessageRepository delegate) {
        this.delegate = delegate;
        this.cache =
                Caffeine.newBuilder()
                        .maximumSize(50_000)
                        .expireAfterWrite(Duration.ofMinutes(5))
                        .build();
    }

    @Override
    public Optional<String> findMessage(String messageKey, Locale locale, @Nullable String tenantId) {
        String cacheKey = cacheKey(messageKey, locale, tenantId);
        return cache.get(cacheKey, k -> delegate.findMessage(messageKey, locale, tenantId));
    }

    private static String cacheKey(String messageKey, Locale locale, @Nullable String tenantId) {
        String tenantPart = tenantId != null ? tenantId : "";
        String loc = locale != null ? locale.toLanguageTag() : "";
        return tenantPart + "\0" + loc + "\0" + messageKey;
    }
}
