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

    private static final String PAYLOAD_ATTRIBUTE =
            JwtBearerPayloadSupport.class.getName() + ".JWT_PAYLOAD";
    private static final Object NO_PAYLOAD = new Object();

    private JwtBearerPayloadSupport() {
    }

    public static JsonNode readPayload(ObjectMapper objectMapper, HttpServletRequest request) {
        Object cached = request.getAttribute(PAYLOAD_ATTRIBUTE);
        if (cached == NO_PAYLOAD) {
            return null;
        }
        if (cached instanceof JsonNode jsonNode) {
            return jsonNode;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            request.setAttribute(PAYLOAD_ATTRIBUTE, NO_PAYLOAD);
            return null;
        }
        String token = authHeader.substring("Bearer ".length()).trim();
        String[] parts = token.split("\\.", 3);
        if (parts.length < 2) {
            request.setAttribute(PAYLOAD_ATTRIBUTE, NO_PAYLOAD);
            return null;
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
            JsonNode payload = objectMapper.readTree(decoded);
            request.setAttribute(PAYLOAD_ATTRIBUTE, payload);
            return payload;
        } catch (Exception e) {
            request.setAttribute(PAYLOAD_ATTRIBUTE, NO_PAYLOAD);
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
