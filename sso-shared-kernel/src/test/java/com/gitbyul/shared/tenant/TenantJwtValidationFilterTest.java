package com.gitbyul.shared.tenant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gitbyul.shared.audit.AuditEvent;
import com.gitbyul.shared.exception.ErrorCode;
import com.gitbyul.shared.exception.I18nMessageKeys;
import com.gitbyul.shared.i18n.SsoRequestLocaleResolver;
import com.gitbyul.shared.util.FixedTimeProvider;
import com.gitbyul.shared.util.RandomIdGenerator;
import com.gitbyul.shared.web.error.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

import static com.gitbyul.shared.testsupport.TestObjectProviders.emptyObjectProvider;
import static com.gitbyul.shared.testsupport.TestObjectProviders.objectProviderOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TenantJwtValidationFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    @AfterEach
    void clearHolder() {
        TenantContextHolder.clear();
    }

    @Test
    void holderUnset_continuesChainWithoutPublishing_evenWithBearer() throws Exception {
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);

        TenantJwtValidationFilter filter =
                new TenantJwtValidationFilter(
                        publisher,
                        objectMapper,
                        new RandomIdGenerator(),
                        new FixedTimeProvider(java.time.Instant.parse("2026-04-08T00:00:00Z")),
                        emptyObjectProvider(),
                        emptyObjectProvider());

        MockHttpServletRequest req = bearerWithTenant("acme-corp", "sub-1");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(200);
        verifyNoInteractions(publisher);
    }

    @Test
    void matchingTenantId_continuesChain() throws Exception {
        TenantContextHolder.set("acme-corp");
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);

        TenantJwtValidationFilter filter =
                new TenantJwtValidationFilter(
                        publisher,
                        objectMapper,
                        new RandomIdGenerator(),
                        new FixedTimeProvider(java.time.Instant.parse("2026-04-08T00:00:00Z")),
                        emptyObjectProvider(),
                        emptyObjectProvider());

        MockHttpServletRequest req = bearerWithTenant("acme-corp", "sub-1");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(200);
        verifyNoInteractions(publisher);
    }

    @Test
    void mismatch_publishesAuditEventAnd403() throws Exception {
        TenantContextHolder.set("acme-corp");
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);

        TenantJwtValidationFilter filter =
                new TenantJwtValidationFilter(
                        publisher,
                        objectMapper,
                        new RandomIdGenerator(),
                        new FixedTimeProvider(java.time.Instant.parse("2026-04-08T00:00:00Z")),
                        emptyObjectProvider(),
                        emptyObjectProvider());

        MockHttpServletRequest req = bearerWithTenant("other-tenant", "sub-1");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(403);
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(publisher).publishEvent(captor.capture());
        AuditEvent event = captor.getValue();
        assertThat(event.eventType()).isEqualTo("SECURITY.TENANT_MISMATCH");
        assertThat(event.tenantId()).isEqualTo("acme-corp");
        assertThat(event.metadata())
                .containsEntry("expectedTenantId", "acme-corp")
                .containsEntry("jwtTenantId", "other-tenant");
    }

    @Test
    void mismatch_writesJsonWhenMessageSourceAndLocaleResolverAvailable() throws Exception {
        TenantContextHolder.set("acme-corp");
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);

        MessageSource messageSource = mock(MessageSource.class);
        when(messageSource.getMessage(
                        eq(I18nMessageKeys.error(ErrorCode.TENANT_MISMATCH)),
                        isNull(),
                        eq(ErrorCode.TENANT_MISMATCH.code()),
                        eq(Locale.KOREA)))
                .thenReturn("테넌트 불일치(로컬라이즈)");

        SsoRequestLocaleResolver localeResolver = mock(SsoRequestLocaleResolver.class);
        when(localeResolver.resolveLocale(any(HttpServletRequest.class))).thenReturn(Locale.KOREA);

        TenantJwtValidationFilter filter =
                new TenantJwtValidationFilter(
                        publisher,
                        objectMapper,
                        new RandomIdGenerator(),
                        new FixedTimeProvider(java.time.Instant.parse("2026-04-08T00:00:00Z")),
                        objectProviderOf(messageSource),
                        objectProviderOf(localeResolver));

        MockHttpServletRequest req = bearerWithTenant("other-tenant", "sub-1");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(res.getContentType()).contains(MediaType.APPLICATION_JSON_VALUE);
        ApiErrorResponse body =
                objectMapper.readValue(res.getContentAsByteArray(), ApiErrorResponse.class);
        assertThat(body.code()).isEqualTo(ErrorCode.TENANT_MISMATCH.code());
        assertThat(body.message()).isEqualTo("테넌트 불일치(로컬라이즈)");

        verify(messageSource)
                .getMessage(
                        eq(I18nMessageKeys.error(ErrorCode.TENANT_MISMATCH)),
                        isNull(),
                        eq(ErrorCode.TENANT_MISMATCH.code()),
                        eq(Locale.KOREA));
        verify(localeResolver).resolveLocale(req);
    }

    @Test
    void missingTenantClaim_whenHolderSet_publishesAuditEventAnd403() throws Exception {
        TenantContextHolder.set("acme-corp");
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);

        TenantJwtValidationFilter filter =
                new TenantJwtValidationFilter(
                        publisher,
                        objectMapper,
                        new RandomIdGenerator(),
                        new FixedTimeProvider(java.time.Instant.parse("2026-04-08T00:00:00Z")),
                        emptyObjectProvider(),
                        emptyObjectProvider());

        MockHttpServletRequest req = bearerWithJsonPayload("{\"sub\":\"sub-1\"}");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(403);
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(publisher).publishEvent(captor.capture());
        AuditEvent event = captor.getValue();
        assertThat(event.eventType()).isEqualTo("SECURITY.TENANT_MISMATCH");
        assertThat(event.tenantId()).isEqualTo("acme-corp");
        assertThat(event.metadata())
                .containsEntry("expectedTenantId", "acme-corp")
                .containsEntry("jwtTenantId", "")
                .containsEntry("reason", "MISSING_TENANT_CLAIM");
    }

    private static MockHttpServletRequest bearerWithTenant(String tenantId, String sub) {
        return bearerWithJsonPayload(
                "{\"tenant_id\":\"%s\",\"sub\":\"%s\"}".formatted(tenantId, sub));
    }

    private static MockHttpServletRequest bearerWithJsonPayload(String jsonPayload) {
        String b64 =
                Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(jsonPayload.getBytes(StandardCharsets.UTF_8));
        String token = "h." + b64 + ".s";
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer " + token);
        return req;
    }
}
