package com.gitbyul.shared.tenant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class JwtBearerPayloadSupportTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void readPayload_decodesTenantId() {
        String payloadJson = "{\"tenant_id\":\"acme-corp\",\"sub\":\"u1\"}";
        String b64 =
                Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        String token = "x." + b64 + ".sig";
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer " + token);

        var node = JwtBearerPayloadSupport.readPayload(mapper, req);
        assertThat(JwtBearerPayloadSupport.claimAsText(node, "tenant_id")).isEqualTo("acme-corp");
        assertThat(JwtBearerPayloadSupport.claimAsText(node, "sub")).isEqualTo("u1");
    }

    @Test
    void readPayload_missingHeader_returnsNull() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        assertThat(JwtBearerPayloadSupport.readPayload(mapper, req)).isNull();
    }
}
