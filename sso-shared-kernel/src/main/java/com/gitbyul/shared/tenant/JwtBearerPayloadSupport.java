package com.gitbyul.shared.tenant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Base64;

/**
 * {@code Authorization: Bearer} JWT의 payload(JSON)를 서명 검증 없이 디코딩한다.
 * <p>
 * 서명·만료 검증은 상위 Security 필터가 담당한다고 가정한다.
 */
public final class JwtBearerPayloadSupport {

    private JwtBearerPayloadSupport() {
    }

    public static JsonNode readPayload(ObjectMapper objectMapper, HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        String token = authHeader.substring("Bearer ".length()).trim();
        String[] parts = token.split("\\.");
        if (parts.length < 2) {
            return null;
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
            return objectMapper.readTree(decoded);
        } catch (Exception e) {
            return null;
        }
    }

    public static String claimAsText(JsonNode payload, String claimName) {
        if (payload == null || claimName == null) {
            return null;
        }
        JsonNode node = payload.get(claimName);
        if (node == null || node.isNull()) {
            return null;
        }
        return node.asText(null);
    }
}
