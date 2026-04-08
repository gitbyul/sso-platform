package com.gitbyul.shared.tenant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gitbyul.shared.audit.AuditEvent;
import com.gitbyul.shared.util.FixedTimeProvider;
import com.gitbyul.shared.util.RandomIdGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class TenantJwtValidationFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    @AfterEach
    void clearHolder() {
        TenantContextHolder.clear();
    }

    @Test
    void matchingTenantId_continuesChain() throws Exception {
        TenantContextHolder.set("acme-corp");
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<?> ms = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<?> lr = mock(ObjectProvider.class);

        TenantJwtValidationFilter filter =
                new TenantJwtValidationFilter(
                        publisher,
                        objectMapper,
                        new RandomIdGenerator(),
                        new FixedTimeProvider(java.time.Instant.parse("2026-04-08T00:00:00Z")),
                        (ObjectProvider) ms,
                        (ObjectProvider) lr);

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
        @SuppressWarnings("unchecked")
        ObjectProvider<?> ms = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<?> lr = mock(ObjectProvider.class);

        TenantJwtValidationFilter filter =
                new TenantJwtValidationFilter(
                        publisher,
                        objectMapper,
                        new RandomIdGenerator(),
                        new FixedTimeProvider(java.time.Instant.parse("2026-04-08T00:00:00Z")),
                        (ObjectProvider) ms,
                        (ObjectProvider) lr);

        MockHttpServletRequest req = bearerWithTenant("other-tenant", "sub-1");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(403);
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(publisher).publishEvent(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo("SECURITY.TENANT_MISMATCH");
    }

    private static MockHttpServletRequest bearerWithTenant(String tenantId, String sub) {
        String payload =
                "{\"tenant_id\":\"%s\",\"sub\":\"%s\"}"
                        .formatted(tenantId, sub);
        String b64 =
                Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        String token = "h." + b64 + ".s";
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer " + token);
        return req;
    }
}
