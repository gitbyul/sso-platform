package com.gitbyul.shared.audit;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 모든 컨텍스트가 공유하는 감사 이벤트 모델.
 */
public record AuditEvent(
        UUID eventId,                 // UUID v7 (시간순 정렬 가능)
        String eventType,            // "AUTHENTICATION.LOGIN_SUCCESS" 형식
        String eventVersion,         // "1.0" (스키마 버전)
        Instant occurredAt,         // 이벤트 발생 시각 (UTC)
        Instant recordedAt,         // DB 기록 시각
        String tenantId,            // 필수
        String tenantDomain,       // 예: "kakaobank.sso.company.com"
        String actorType,          // "USER" | "SYSTEM" | "ADMIN" | "SERVICE"
        String actorId,
        String actorEmail,         // 마스킹 적용: user****@domain.com
        String actorIp,            // IPv4/IPv6
        String targetType,         // "USER" | "CLIENT" | "TOKEN" | "SESSION"
        String targetId,
        String result,             // "SUCCESS" | "FAILURE" | "PARTIAL"
        String failureReason,      // null if SUCCESS
        String traceId,            // OpenTelemetry TraceID (MDC에서 추출)
        String sessionId,
        String clientId,
        String userAgent,
        String deviceFingerprint,   // SHA-256(IP + UserAgent + 기타)
        String geoCountry,         // ISO 3166-1 alpha-2
        String geoCity,
        JsonNode beforeState,      // 변경 전 (민감 필드 제거 후)
        JsonNode afterState,       // 변경 후 (민감 필드 제거 후)
        Map<String, String> metadata, // 이벤트 타입별 추가 데이터
        String checksum            // HMAC-SHA256(eventId + eventType + occurredAt + actorId + targetId + result)
) {
}

