package com.gitbyul.shared.i18n;

import java.util.Locale;
import java.util.Optional;
import org.springframework.lang.Nullable;

/**
 * 플랫폼/테넌트 DB 번역 조회 포트. 쓰기는 관리 API·마이그레이션에서만 수행한다.
 */
public interface LocalizedMessageRepository {

    /**
     * @param tenantId 테넌트 오버레이 조회 시 값, 플랫폼 기본만 쓸 때는 {@code null}
     */
    Optional<String> findMessage(String messageKey, Locale locale, @Nullable String tenantId);
}
