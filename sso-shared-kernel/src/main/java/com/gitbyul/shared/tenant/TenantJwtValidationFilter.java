package com.gitbyul.shared.tenant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gitbyul.shared.audit.AuditEvent;
import com.gitbyul.shared.exception.ErrorCode;
import com.gitbyul.shared.i18n.SsoRequestLocaleResolver;
import com.gitbyul.shared.util.RandomIdGenerator;
import com.gitbyul.shared.util.TimeProvider;
import com.gitbyul.shared.web.error.ApiErrorResponses;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * JWT {@code tenant_id} 클레임과 {@link TenantContextHolder} 값을 일치 검증.
 * <p>
 * 불일치·JWT {@code tenant_id} 누락 시 {@code 403} 및 감사 이벤트 {@code SECURITY.TENANT_MISMATCH} 발행.
 */
public class TenantJwtValidationFilter extends OncePerRequestFilter implements Ordered {

    private static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 40;

    private static final String TENANT_ID_CLAIM = "tenant_id";
    private static final String EVENT_TYPE = "SECURITY.TENANT_MISMATCH";
    private static final String EVENT_VERSION = "1.0";

    private final ApplicationEventPublisher publisher;
    private final ObjectMapper objectMapper;
    private final RandomIdGenerator idGenerator;
    private final TimeProvider timeProvider;
    private final ObjectProvider<MessageSource> messageSource;
    private final ObjectProvider<SsoRequestLocaleResolver> localeResolver;

    public TenantJwtValidationFilter(
            ApplicationEventPublisher publisher,
            ObjectMapper objectMapper,
            RandomIdGenerator idGenerator,
            TimeProvider timeProvider,
            ObjectProvider<MessageSource> messageSource,
            ObjectProvider<SsoRequestLocaleResolver> localeResolver) {
        this.publisher = publisher;
        this.objectMapper = objectMapper;
        this.idGenerator = idGenerator;
        this.timeProvider = timeProvider;
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
        String holderTenantId = TenantContextHolder.get();
        if (holderTenantId == null) {
            filterChain.doFilter(request, response);
            return;
        }

        JsonNode jwtPayload = JwtBearerPayloadSupport.readPayload(objectMapper, request);
        String jwtTenantId = JwtBearerPayloadSupport.claimAsText(jwtPayload, TENANT_ID_CLAIM);

        if (jwtPayload == null) {
            filterChain.doFilter(request, response);
            return;
        }
        if (jwtTenantId == null || !jwtTenantId.equals(holderTenantId)) {
            publishTenantMismatchAuditEvent(request, jwtTenantId, holderTenantId, jwtPayload);
            writeTenantMismatch(request, response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void writeTenantMismatch(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        MessageSource ms = messageSource.getIfAvailable();
        SsoRequestLocaleResolver lr = localeResolver.getIfAvailable();
        if (ms != null && lr != null) {
            ApiErrorResponses.writeJson(
                    response,
                    objectMapper,
                    ms,
                    lr.resolveLocale(request),
                    ErrorCode.TENANT_MISMATCH,
                    ErrorCode.TENANT_MISMATCH.httpStatus().value());
            return;
        }
        if (ms != null) {
            ApiErrorResponses.writeJson(
                    response,
                    objectMapper,
                    ms,
                    Locale.getDefault(),
                    ErrorCode.TENANT_MISMATCH,
                    ErrorCode.TENANT_MISMATCH.httpStatus().value());
            return;
        }
        response.sendError(
                ErrorCode.TENANT_MISMATCH.httpStatus().value(), ErrorCode.TENANT_MISMATCH.code());
    }

    private void publishTenantMismatchAuditEvent(
            HttpServletRequest request,
            String jwtTenantId,
            String holderTenantId,
            JsonNode jwtPayload) {
        Instant now = timeProvider.now();
        UUID eventId = idGenerator.generateUuidV7();

        String tenantDomain = request.getServerName();
        String traceId = MDC.get("traceId");
        String actorIp = request.getRemoteAddr();

        String actorId = JwtBearerPayloadSupport.claimAsText(jwtPayload, "sub");
        String targetId = JwtBearerPayloadSupport.claimAsText(jwtPayload, "jti");

        Map<String, String> metadata = new HashMap<>();
        metadata.put("expectedTenantId", holderTenantId);
        metadata.put("jwtTenantId", jwtTenantId != null ? jwtTenantId : "");
        if (jwtTenantId == null) {
            metadata.put("reason", "MISSING_TENANT_CLAIM");
        }

        // 감사 스트림 소유·라우팅: 요청 컨텍스트 테넌트(holder). JWT 측 값은 metadata 로 보존.
        String auditTenantId = holderTenantId;

        String actorType = "SERVICE";
        String result = "FAILURE";
        String failureReason = "TENANT_MISMATCH";

        String userAgent = request.getHeader("User-Agent");

        AuditEvent auditEvent = new AuditEvent(
                eventId,
                EVENT_TYPE,
                EVENT_VERSION,
                now,
                now,
                auditTenantId,
                tenantDomain,
                actorType,
                actorId,
                null,
                actorIp,
                "TOKEN",
                targetId,
                result,
                failureReason,
                traceId,
                null,
                null,
                userAgent,
                null,
                null,
                null,
                null,
                null,
                metadata,
                null
        );

        publisher.publishEvent(auditEvent);
    }
}
