package com.gitbyul.shared.tenant;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class MdcPropagationFilterTest {

    @Test
    void putsUserAndClientFromJwtClaims() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        MdcPropagationFilter filter = new MdcPropagationFilter(mapper);

        String payload =
                "{\"sub\":\"user-99\",\"azp\":\"client-app\"}";
        String b64 =
                Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/api/x");
        req.addHeader("Authorization", "Bearer h." + b64 + ".s");
        MockHttpServletResponse res = new MockHttpServletResponse();
        AtomicBoolean reached = new AtomicBoolean(false);
        FilterChain chain =
                (request, response) -> {
                    assertThat(MDC.get("userId")).isEqualTo("user-99");
                    assertThat(MDC.get("clientId")).isEqualTo("client-app");
                    reached.set(true);
                };

        filter.doFilter(req, res, chain);

        assertThat(reached.get()).isTrue();
        assertThat(MDC.get("userId")).isNull();
        assertThat(MDC.get("clientId")).isNull();
    }
}
