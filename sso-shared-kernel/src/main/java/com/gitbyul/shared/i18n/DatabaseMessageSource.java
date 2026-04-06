package com.gitbyul.shared.i18n;

import com.gitbyul.shared.tenant.TenantContextHolder;
import java.text.MessageFormat;
import java.util.Locale;
import org.springframework.context.support.AbstractMessageSource;

/**
 * DB {@link LocalizedMessageRepository} 기반 {@link org.springframework.context.MessageSource}.
 */
public class DatabaseMessageSource extends AbstractMessageSource {

    private final LocalizedMessageRepository localizedMessageRepository;

    public DatabaseMessageSource(LocalizedMessageRepository localizedMessageRepository) {
        this.localizedMessageRepository = localizedMessageRepository;
        setAlwaysUseMessageFormat(false);
    }

    @Override
    protected MessageFormat resolveCode(String code, Locale locale) {
        String text = resolveCodeWithoutArguments(code, locale);
        if (text != null) {
            return new MessageFormat(text, locale);
        }
        return null;
    }

    @Override
    protected String resolveCodeWithoutArguments(String code, Locale locale) {
        if (code == null || locale == null) {
            return null;
        }
        return localizedMessageRepository
                .findMessage(code, locale, TenantContextHolder.get())
                .orElse(null);
    }
}
