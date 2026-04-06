package com.gitbyul.shared.tenant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gitbyul.shared.exception.ErrorCode;
import com.gitbyul.shared.i18n.SsoRequestLocaleResolver;
import com.gitbyul.shared.web.error.ApiErrorResponses;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.MessageSource;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 요청 단위 테넌트 식별.
 * <p>
 * 규칙 (agent-tenancy-and-filters):
 * <ul>
 *   <li>서브도메인 우선 ({tenantId}.host…)</li>
 *   <li>로컬/IP 호스트에서는 서브도메인이 없으므로 이 단계는 {@code null}</li>
 *   <li>Bearer JWT에 {@code tenant_id}가 있으면 그 값을 사용 (M2M 포함, {@code X-Tenant-ID}와 혼용 금지)</li>
 *   <li>{@code production} 프로파일에서는 서브도메인·JWT 모두 실패 시 {@code X-Tenant-ID} 허용 안 함</li>
 *   <li>{@code local}/{@code dev} 등 비운영에서는 마지막으로 {@code X-Tenant-ID} 허용</li>
 *   <li>{@code /.well-known/**}, {@code /actuator/**} 는 스킵</li>
 * </ul>
 */
public class TenantResolutionFilter extends OncePerRequestFilter implements Ordered {

    private static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 20;

    private static final String TENANT_HEADER = "X-Tenant-ID";
    private static final String MDC_TENANT_ID = "tenantId";

    private static final Pattern IPV4 = Pattern.compile("^(\\d{1,3}\\.){3}\\d{1,3}$");

    private final Environment environment;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<MessageSource> messageSource;
    private final ObjectProvider<SsoRequestLocaleResolver> localeResolver;

    public TenantResolutionFilter(
            Environment environment,
            ObjectMapper objectMapper,
            ObjectProvider<MessageSource> messageSource,
            ObjectProvider<SsoRequestLocaleResolver> localeResolver) {
        this.environment = environment;
        this.objectMapper = objectMapper;
        this.messageSource = messageSource;
        this.localeResolver = localeResolver;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (shouldSkip(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String tenantId = extractFromSubdomainOnly(request);

            if (tenantId == null) {
                tenantId = tenantIdFromJwt(request);
            }

            if (tenantId == null && !isProductionProfile()) {
                tenantId = request.getHeader(TENANT_HEADER);
            }

            if (tenantId == null || !isValidTenantId(tenantId)) {
                writeTenantNotIdentified(request, response);
                return;
            }

            TenantContextHolder.set(tenantId);
            MDC.put(MDC_TENANT_ID, tenantId);
            filterChain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
            MDC.remove(MDC_TENANT_ID);
        }
    }

    private boolean isProductionProfile() {
        return environment != null && environment.acceptsProfiles(Profiles.of("production"));
    }

    private boolean shouldSkip(String path) {
        if (path == null) {
            return false;
        }
        return path.startsWith("/actuator/") || path.startsWith("/.well-known/");
    }

    /**
     * 서브도메인 첫 세그먼트만 반환. localhost/IPv4/IPv6 는 테넌트 없음 → {@code null}.
     * (헤더는 이 메서드에서 읽지 않음 — 운영에서 헤더 fallback 금지와 충돌 방지)
     */
    private String extractFromSubdomainOnly(HttpServletRequest request) {
        String serverName = request.getServerName();
        if (serverName == null || serverName.isBlank()) {
            return null;
        }
        if (isLocalOrIp(serverName)) {
            return null;
        }
        String[] parts = serverName.split("\\.");
        if (parts.length < 2) {
            return null;
        }
        return parts[0];
    }

    private boolean isLocalOrIp(String serverName) {
        String s = serverName.toLowerCase();
        if (s.equals("localhost") || s.startsWith("127.") || s.equals("::1")) {
            return true;
        }
        if (s.contains(":")) {
            return true;
        }
        return IPV4.matcher(s).matches();
    }

    private boolean isValidTenantId(String tenantId) {
        if (tenantId == null) {
            return false;
        }
        String s = tenantId.trim().toLowerCase();
        return s.matches("^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$");
    }

    private String tenantIdFromJwt(HttpServletRequest request) {
        JsonNode payload = JwtBearerPayloadSupport.readPayload(objectMapper, request);
        return JwtBearerPayloadSupport.claimAsText(payload, "tenant_id");
    }

    private void writeTenantNotIdentified(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        MessageSource ms = messageSource.getIfAvailable();
        SsoRequestLocaleResolver lr = localeResolver.getIfAvailable();
        if (ms != null && lr != null) {
            ApiErrorResponses.writeJson(
                    response,
                    objectMapper,
                    ms,
                    lr.resolveLocale(request),
                    ErrorCode.TENANT_NOT_IDENTIFIED,
                    ErrorCode.TENANT_NOT_IDENTIFIED.httpStatus().value());
            return;
        }
        if (ms != null) {
            ApiErrorResponses.writeJson(
                    response,
                    objectMapper,
                    ms,
                    Locale.getDefault(),
                    ErrorCode.TENANT_NOT_IDENTIFIED,
                    ErrorCode.TENANT_NOT_IDENTIFIED.httpStatus().value());
            return;
        }
        response.sendError(
                ErrorCode.TENANT_NOT_IDENTIFIED.httpStatus().value(),
                ErrorCode.TENANT_NOT_IDENTIFIED.code());
    }
}
