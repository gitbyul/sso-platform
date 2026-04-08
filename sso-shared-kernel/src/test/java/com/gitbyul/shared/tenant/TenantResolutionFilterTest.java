package com.gitbyul.shared.tenant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gitbyul.shared.i18n.SsoRequestLocaleResolver;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.MessageSource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
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
        ObjectProvider<MessageSource> ms = mockObjectProvider();
        ObjectProvider<SsoRequestLocaleResolver> lr = mockObjectProvider();

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
        ObjectProvider<MessageSource> ms = mockObjectProvider();
        ObjectProvider<SsoRequestLocaleResolver> lr = mockObjectProvider();

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
    void skipsActuator() throws Exception {
        Environment env = mock(Environment.class);
        ObjectProvider<MessageSource> ms = mockObjectProvider();
        ObjectProvider<SsoRequestLocaleResolver> lr = mockObjectProvider();
        TenantResolutionFilter filter =
                new TenantResolutionFilter(env, objectMapper, ms, lr);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/actuator/health");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(200);
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> mockObjectProvider() {
        return mock(ObjectProvider.class);
    }
}
