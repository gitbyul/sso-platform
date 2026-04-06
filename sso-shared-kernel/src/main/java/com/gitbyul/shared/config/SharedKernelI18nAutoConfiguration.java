package com.gitbyul.shared.config;

import com.gitbyul.shared.i18n.CaffeineCachingLocalizedMessageRepository;
import com.gitbyul.shared.i18n.DatabaseMessageSource;
import com.gitbyul.shared.i18n.LocalizedMessageRepository;
import com.gitbyul.shared.i18n.SsoLocaleContextFilter;
import com.gitbyul.shared.i18n.SsoRequestLocaleResolver;
import com.gitbyul.shared.i18n.TenantDefaultLocaleLookup;
import com.gitbyul.shared.i18n.jpa.JpaLocalizedMessageRepository;
import com.gitbyul.shared.i18n.jpa.JpaTenantDefaultLocaleLookup;
import com.gitbyul.shared.i18n.jpa.LocalizedMessageJpaRepository;
import com.gitbyul.shared.web.error.SsoRestExceptionHandlers;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

/**
 * DB 기반 {@link MessageSource}, 로케일 필터, REST 예외 처리.
 */
@AutoConfiguration
@ConditionalOnClass(name = "jakarta.servlet.Servlet")
@ConditionalOnBean(jakarta.persistence.EntityManagerFactory.class)
@EnableJpaRepositories(basePackageClasses = LocalizedMessageJpaRepository.class)
public class SharedKernelI18nAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AcceptHeaderLocaleResolver acceptHeaderLocaleResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setDefaultLocale(Locale.ENGLISH);
        return resolver;
    }

    @Bean
    @ConditionalOnBean(EntityManager.class)
    public TenantDefaultLocaleLookup tenantDefaultLocaleLookup(EntityManager entityManager) {
        return new JpaTenantDefaultLocaleLookup(entityManager);
    }

    @Bean
    public LocalizedMessageRepository localizedMessageRepository(LocalizedMessageJpaRepository jpaRepository) {
        return new CaffeineCachingLocalizedMessageRepository(new JpaLocalizedMessageRepository(jpaRepository));
    }

    @Bean
    public SsoRequestLocaleResolver ssoRequestLocaleResolver(
            TenantDefaultLocaleLookup tenantDefaultLocaleLookup,
            AcceptHeaderLocaleResolver acceptHeaderLocaleResolver) {
        return new SsoRequestLocaleResolver(tenantDefaultLocaleLookup, acceptHeaderLocaleResolver);
    }

    @Bean
    public SsoLocaleContextFilter ssoLocaleContextFilter(SsoRequestLocaleResolver ssoRequestLocaleResolver) {
        return new SsoLocaleContextFilter(ssoRequestLocaleResolver);
    }

    @Bean
    @Primary
    public MessageSource messageSource(LocalizedMessageRepository localizedMessageRepository) {
        DatabaseMessageSource database = new DatabaseMessageSource(localizedMessageRepository);
        ReloadableResourceBundleMessageSource fallback = new ReloadableResourceBundleMessageSource();
        fallback.setBasename("classpath:messages");
        fallback.setDefaultEncoding(StandardCharsets.UTF_8.name());
        fallback.setFallbackToSystemLocale(false);
        fallback.setDefaultLocale(Locale.ENGLISH);
        database.setParentMessageSource(fallback);
        return database;
    }

    @Bean
    @Primary
    public LocaleResolver localeResolver(SsoRequestLocaleResolver ssoRequestLocaleResolver) {
        return new LocaleResolver() {
            @Override
            public Locale resolveLocale(HttpServletRequest request) {
                return ssoRequestLocaleResolver.resolveLocale(request);
            }

            @Override
            public void setLocale(
                    HttpServletRequest request, HttpServletResponse response, Locale locale) {
                LocaleContextHolder.setLocale(locale, true);
            }
        };
    }

    @Bean
    public SsoRestExceptionHandlers ssoRestExceptionHandlers(MessageSource messageSource) {
        return new SsoRestExceptionHandlers(messageSource);
    }
}
