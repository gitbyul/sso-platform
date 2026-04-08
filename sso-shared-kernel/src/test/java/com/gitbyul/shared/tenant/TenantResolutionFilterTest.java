package com.gitbyul.shared.tenant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TenantResolutionFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    @AfterEach
    void clearHolder() {
        TenantContextHolder.clear();
    }

    @Test
    void nonProduction_allowsXTenantIdFallback() throws Exception {
        Environment env = mock(Environment.class);
        when(env.acceptsProfiles(Profiles.of("production"))).thenReturn(false);
        @SuppressWarnings("unchecked")
        ObjectProvider<?> ms = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<?> lr = mock(ObjectProvider.class);

        TenantResolutionFilter filter =
                new TenantResolutionFilter(env, objectMapper, (ObjectProvider) ms, (ObjectProvider) lr);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/api/ping");
        req.setServerName("localhost");
        req.addHeader("X-Tenant-ID", "acme-corp");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    void production_withoutTenant_returns400() throws Exception {
        Environment env = mock(Environment.class);
        when(env.acceptsProfiles(Profiles.of("production"))).thenReturn(true);
        @SuppressWarnings("unchecked")
        ObjectProvider<?> ms = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<?> lr = mock(ObjectProvider.class);

        TenantResolutionFilter filter =
                new TenantResolutionFilter(env, objectMapper, (ObjectProvider) ms, (ObjectProvider) lr);

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
        @SuppressWarnings("unchecked")
        ObjectProvider<?> ms = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<?> lr = mock(ObjectProvider.class);
        TenantResolutionFilter filter =
                new TenantResolutionFilter(env, objectMapper, (ObjectProvider) ms, (ObjectProvider) lr);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/actuator/health");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(200);
    }
}
