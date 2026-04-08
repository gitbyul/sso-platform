package com.gitbyul.shared.tenant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gitbyul.shared.exception.ErrorCode;
import com.gitbyul.shared.exception.I18nMessageKeys;
import com.gitbyul.shared.i18n.SsoRequestLocaleResolver;
import com.gitbyul.shared.web.error.ApiErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.MessageSource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.gitbyul.shared.testsupport.TestObjectProviders.emptyObjectProvider;
import static com.gitbyul.shared.testsupport.TestObjectProviders.objectProviderOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantResolutionFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    @AfterEach
    void clearHolder() {
        TenantContextHolder.clear();
        MDC.clear();
    }

    @Test
    void nonProduction_allowsXTenantIdFallback() throws Exception {
        Environment env = mock(Environment.class);
        when(env.acceptsProfiles(Profiles.of("production"))).thenReturn(false);
        ObjectProvider<MessageSource> ms = emptyObjectProvider();
        ObjectProvider<SsoRequestLocaleResolver> lr = emptyObjectProvider();

        TenantResolutionFilter filter =
                new TenantResolutionFilter(env, objectMapper, ms, lr);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/api/ping");
        req.setServerName("localhost");
        req.addHeader("X-Tenant-ID", "acme-corp");
        MockHttpServletResponse res = new MockHttpServletResponse();
        AtomicBoolean reached = new AtomicBoolean(false);
        FilterChain chain =
                (request, response) -> {
                    assertThat(TenantContextHolder.get()).isEqualTo("acme-corp");
                    assertThat(MDC.get("tenantId")).isEqualTo("acme-corp");
                    reached.set(true);
                };

        filter.doFilter(req, res, chain);

        assertThat(reached.get()).isTrue();
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    void production_withoutTenant_returns400() throws Exception {
        Environment env = mock(Environment.class);
        when(env.acceptsProfiles(Profiles.of("production"))).thenReturn(true);
        ObjectProvider<MessageSource> ms = emptyObjectProvider();
        ObjectProvider<SsoRequestLocaleResolver> lr = emptyObjectProvider();

        TenantResolutionFilter filter =
                new TenantResolutionFilter(env, objectMapper, ms, lr);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/api/ping");
        req.setServerName("localhost");
        req.addHeader("X-Tenant-ID", "acme-corp");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(400);
    }

    @Test
    void production_withoutTenant_writesJsonWhenMessageSourceAndLocaleResolverAvailable() throws Exception {
        Environment env = mock(Environment.class);
        when(env.acceptsProfiles(Profiles.of("production"))).thenReturn(true);

        MessageSource messageSource = mock(MessageSource.class);
        when(messageSource.getMessage(
                        eq(I18nMessageKeys.error(ErrorCode.TENANT_NOT_IDENTIFIED)),
                        isNull(),
                        eq(ErrorCode.TENANT_NOT_IDENTIFIED.code()),
                        eq(Locale.KOREA)))
                .thenReturn("로컬라이즈된 테넌트 안내");

        SsoRequestLocaleResolver localeResolver = mock(SsoRequestLocaleResolver.class);
        when(localeResolver.resolveLocale(any(HttpServletRequest.class))).thenReturn(Locale.KOREA);

        TenantResolutionFilter filter =
                new TenantResolutionFilter(
                        env,
                        objectMapper,
                        objectProviderOf(messageSource),
                        objectProviderOf(localeResolver));

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/api/ping");
        req.setServerName("localhost");
        req.addHeader("X-Tenant-ID", "acme-corp");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(400);
        assertThat(res.getContentType()).contains(MediaType.APPLICATION_JSON_VALUE);
        ApiErrorResponse body =
                objectMapper.readValue(res.getContentAsByteArray(), ApiErrorResponse.class);
        assertThat(body.code()).isEqualTo(ErrorCode.TENANT_NOT_IDENTIFIED.code());
        assertThat(body.message()).isEqualTo("로컬라이즈된 테넌트 안내");

        verify(messageSource)
                .getMessage(
                        eq(I18nMessageKeys.error(ErrorCode.TENANT_NOT_IDENTIFIED)),
                        isNull(),
                        eq(ErrorCode.TENANT_NOT_IDENTIFIED.code()),
                        eq(Locale.KOREA));
        verify(localeResolver).resolveLocale(req);
    }

    @Test
    void skipsActuator() throws Exception {
        Environment env = mock(Environment.class);
        ObjectProvider<MessageSource> ms = emptyObjectProvider();
        ObjectProvider<SsoRequestLocaleResolver> lr = emptyObjectProvider();
        TenantResolutionFilter filter =
                new TenantResolutionFilter(env, objectMapper, ms, lr);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/actuator/health");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(200);
    }
}
