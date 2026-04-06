package com.gitbyul.shared.tenant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 인증된 Bearer JWT에서 {@code userId}·{@code clientId}를 MDC에 주입한다.
 * <p>
 * 표준 MDC 키: {@code userId} ← {@code sub}, {@code clientId} ← {@code client_id} 또는 {@code azp}.
 * {@link TenantResolutionFilter}가 설정한 {@code tenantId}와 별도로 관리한다.
 */
public class MdcPropagationFilter extends OncePerRequestFilter implements Ordered {

    private static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 30;

    private static final String MDC_USER_ID = "userId";
    private static final String MDC_CLIENT_ID = "clientId";

    private final ObjectMapper objectMapper;

    public MdcPropagationFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (shouldSkip(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            JsonNode payload = JwtBearerPayloadSupport.readPayload(objectMapper, request);
            if (payload != null) {
                String sub = JwtBearerPayloadSupport.claimAsText(payload, "sub");
                if (sub != null && !sub.isBlank()) {
                    MDC.put(MDC_USER_ID, sub);
                }
                String clientId = JwtBearerPayloadSupport.claimAsText(payload, "client_id");
                if (clientId == null || clientId.isBlank()) {
                    clientId = JwtBearerPayloadSupport.claimAsText(payload, "azp");
                }
                if (clientId != null && !clientId.isBlank()) {
                    MDC.put(MDC_CLIENT_ID, clientId);
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_USER_ID);
            MDC.remove(MDC_CLIENT_ID);
        }
    }

    private boolean shouldSkip(String path) {
        if (path == null) {
            return false;
        }
        return path.startsWith("/actuator/") || path.startsWith("/.well-known/");
    }
}
