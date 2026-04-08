package com.gitbyul.shared.audit;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuditEventTest {

    @Test
    void recordHoldsCoreFields() {
        UUID id = UUID.randomUUID();
        Instant at = Instant.parse("2026-04-08T12:00:00Z");
        Map<String, String> meta = Map.of("k", "v");
        AuditEvent e =
                new AuditEvent(
                        id,
                        "SECURITY.TEST",
                        "1.0",
                        at,
                        at,
                        "tenant-a",
                        "t.example.com",
                        "USER",
                        "actor-1",
                        null,
                        "127.0.0.1",
                        "TOKEN",
                        "tgt-1",
                        "SUCCESS",
                        null,
                        "trace",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        meta,
                        null);
        assertThat(e.eventId()).isEqualTo(id);
        assertThat(e.eventType()).isEqualTo("SECURITY.TEST");
        assertThat(e.tenantId()).isEqualTo("tenant-a");
        assertThat(e.metadata()).isEqualTo(meta);
    }
}
