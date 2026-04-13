package com.gitbyul.shared.util;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RandomIdGeneratorTest {

    private static long unixTsMsFromUuidV7(UUID u) {
        return (u.getMostSignificantBits() >>> 16) & 0xFFFFFFFFFFFFL;
    }

    @Test
    void generateUuidV7_timestamp48BitsTracksCurrentMillis() {
        RandomIdGenerator gen = new RandomIdGenerator();
        long before = System.currentTimeMillis() & 0xFFFFFFFFFFFFL;
        UUID u = gen.generateUuidV7();
        long after = System.currentTimeMillis() & 0xFFFFFFFFFFFFL;
        long fromUuid = unixTsMsFromUuidV7(u);
        assertThat(fromUuid).isBetween(before, after);
    }

    @Test
    void generateUuidV7_withInstant_usesProvidedTimestamp() {
        RandomIdGenerator gen = new RandomIdGenerator();
        Instant timestamp = Instant.parse("2026-04-08T00:00:00Z");

        UUID u = gen.generateUuidV7(timestamp);

        assertThat(unixTsMsFromUuidV7(u)).isEqualTo(timestamp.toEpochMilli() & 0xFFFFFFFFFFFFL);
    }

    @Test
    void generateUuidV7_hasVersion7Variant2AndDistinct() {
        RandomIdGenerator gen = new RandomIdGenerator();
        Set<UUID> seen = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            UUID u = gen.generateUuidV7();
            assertThat(u.version()).as("RFC 9562 version nibble").isEqualTo(7);
            assertThat(u.variant()).as("RFC 4122 IETF variant").isEqualTo(2);
            assertThat(seen.add(u)).as("collision-free in sample").isTrue();
        }
    }

    @Test
    void generateUuidV7_withInstant_rejectsNull() {
        RandomIdGenerator gen = new RandomIdGenerator();
        assertThatThrownBy(() -> gen.generateUuidV7(null)).isInstanceOf(NullPointerException.class);
    }
}
