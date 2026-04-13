package com.gitbyul.shared.util;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class FixedTimeProviderTest {

    @Test
    void returnsFixedInstant() {
        Instant t = Instant.parse("2026-01-02T03:04:05Z");
        TimeProvider tp = new FixedTimeProvider(t);
        assertThat(tp.now()).isEqualTo(t);
    }
}
